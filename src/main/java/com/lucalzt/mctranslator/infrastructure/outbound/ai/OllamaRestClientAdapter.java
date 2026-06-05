package com.lucalzt.mctranslator.infrastructure.outbound.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.domain.service.JsonSanitizer;
import com.lucalzt.mctranslator.ports.outbound.TranslationEnginePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Adaptador de salida (Secondary Adapter) encargado de conectarse con el servidor local de Ollama.
 * * Envía lotes de traducciones utilizando peticiones REST, sanitiza y parsea el resultado devuelto.
 * * Implementa TranslationEnginePort.
 */
@Component
public class OllamaRestClientAdapter implements TranslationEnginePort {

    private static final System.Logger LOGGER = System.getLogger(OllamaRestClientAdapter.class.getName());

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final JsonSanitizer jsonSanitizer;

    @Value("${mctranslator.ollama.url:http://localhost:11434}")
    private String ollamaBaseUrl = "http://localhost:11434";

    @Value("${mctranslator.ollama.model:llama3.2:3b}")
    private String modelName = "llama3.2:3b";

    /**
     * Construye el adaptador inyectando los componentes de infraestructura y dominio necesarios.
     */
    public OllamaRestClientAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "El ObjectMapper de Jackson no puede ser nulo");
        this.restClient = restClientBuilder.build();
        this.jsonSanitizer = new JsonSanitizer(); // Instancio directamente el servicio utilitario del dominio
    }

    @Override
    public TranslationResult translate(TranslationChunk chunk) {
        Objects.requireNonNull(chunk, "El lote de traducción (chunk) no puede ser nulo");

        LOGGER.log(System.Logger.Level.INFO, "Enviando petición de traducción al modelo local de Ollama: '{}'. Lote ID: {}", modelName, chunk.chunkId());

        String prompt = buildPrompt(chunk.translationsToTranslate());

        OllamaRequest requestPayload = new OllamaRequest(
                modelName,
                prompt,
                false, // False para desactivar el streaming para recibir el payload completo de una vez
                "json", // Opción para forzar el modo JSON en la API de Ollama
                new OllamaOptions(0.1) // Temperatura en 0.1 para maximizar la precisión lógica y evitar alucinaciones
        );

        try {
            LOGGER.log(System.Logger.Level.DEBUG, "Realizando llamada POST a la API de Ollama en: {}/api/generate", ollamaBaseUrl);

            OllamaResponse rawResponse = restClient.post()
                    .uri(ollamaBaseUrl + "/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(OllamaResponse.class);

            if (rawResponse == null || rawResponse.response == null) {
                LOGGER.log(System.Logger.Level.ERROR, "Ollama retornó una respuesta nula o vacía para el lote ID: {}", chunk.chunkId());
                throw new RuntimeException("La respuesta obtenida desde la API de Ollama fue inválida.");
            }

            // 1. Sanitizo la respuesta de texto plano extraído de Ollama usando el sanitizador del dominio
            String sanitizedTextJson = jsonSanitizer.sanitize(rawResponse.response());
            LOGGER.log(System.Logger.Level.DEBUG, "Respuesta del LLM local sanitizada de forma exitosa para el procesamiento de Jackson.");

            // 2. Deserializo la cadena limpia a mapa estructurado Map<String, String>
            Map<String, String> translatedMap = objectMapper.readValue(
                    sanitizedTextJson,
                    new TypeReference<Map<String, String>>() {}
            );

            LOGGER.log(System.Logger.Level.INFO, "Lote ID {} traducido con éxito. Se recuperaron {} claves localizadas.", chunk.chunkId(), translatedMap.size());
            return new TranslationResult(chunk.chunkId(), translatedMap, Instant.now());

        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Fallo de red o parseo en el adaptador de traducción de Ollama para el lote ID: " + chunk.chunkId(), e);
            throw new RuntimeException("Error en la llamada de infraestructura de traducción de Ollama", e);
        }
    }

    private String buildPrompt(Map<String, String> sourceTranslations) {
        try {
            String jsonInputString = objectMapper.writeValueAsString(sourceTranslations);

            return """
                   Eres un traductor experto en localización de mods de Minecraft, especializado en modpacks de rol (RPG).
                   Tu única tarea es traducir mapas JSON de idiomas del inglés al español de España (es_es).
                   
                   REGLAS ABSOLUTAS E INVIOLABLES:
                   1. Traduce ÚNICAMENTE los valores del JSON. NUNCA debes alterar, traducir ni modificar las claves bajo ningún motivo.
                   2. Conserva exactamente todos los códigos de color, formato e íconos internos de Minecraft (por ejemplo: '§a', '§r', '%s', '%d', '%1$s', '{0}', '{1}', '\n', etc.).
                   3. Mantén un tono de fantasía/RPG medieval adaptado al estilo de juego de Minecraft. Usa terminología estándar en español para ítems (por ejemplo, 'Iron' → 'Hierro', 'Chest' → 'Cofre').
                   4. PROTECCIÓN DE NOMBRES DE MODS: NO traduzcas bajo ningún concepto los nombres propios de los mods (por ejemplo: 'Bosses of Mass Destruction', 'Simply Swords', 'BetterEnd', 'Mythic Upgrades', 'Archon', etc.). Deben permanecer en inglés para no romper documentación ni referencias externas.
                   5. PROTECCIÓN DE METADATOS: Si detectas que el valor corresponde a un menú técnico de configuración o metadatos de ModMenu, tradúcelo de forma híbrida conservando el nombre del mod original (ej: 'Bosses of Mass Destruction Config' → 'Configuración de Bosses of Mass Destruction').
                   6. Debes responder estrictamente con un objeto JSON plano. No incluyas explicaciones, introducciones, saludos ni bloques de código Markdown.
                   
                   OBJETO JSON A TRADUCIR:
                   """ + jsonInputString;

        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Error crítico de infraestructura al serializar el chunk a JSON para el Prompt", e);
            throw new RuntimeException("Fallo al construir el prompt de traducción", e);
        }
    }

    // --- Records de ayuda de infraestructura interna para serialización ---
    private record OllamaRequest(String model, String prompt, boolean stream, String format, OllamaOptions options) {}
    private record OllamaOptions(double temperature) {}
    private record OllamaResponse(String response) {}
}
