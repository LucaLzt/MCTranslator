package com.lucalzt.mctranslator.infrastructure.outbound.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.domain.service.JsonSanitizer;
import com.lucalzt.mctranslator.infrastructure.config.EngineRegistry;
import com.lucalzt.mctranslator.ports.outbound.TranslationEnginePort;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Component
public class OllamaRestClientAdapter implements TranslationEnginePort {

    private static final System.Logger LOGGER = System.getLogger(OllamaRestClientAdapter.class.getName());

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 2000L;
    private static final long MAX_BACKOFF_MS = 16000L;

    private final RestClient.Builder restClientBuilder;
    private RestClient restClient;
    private final ObjectMapper objectMapper;
    private final JsonSanitizer jsonSanitizer;
    private volatile String modelName;

    private EngineRegistry engineRegistry;

    @Autowired
    public void setEngineRegistry(EngineRegistry engineRegistry) {
        this.engineRegistry = engineRegistry;
    }

    @PostConstruct
    public void register() {
        if (engineRegistry != null) {
            engineRegistry.register("ollama", this);
        }
    }

    public OllamaRestClientAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${mctranslator.ollama.url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${mctranslator.ollama.model:mc-test}") String modelName
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "El ObjectMapper de Jackson no puede ser nulo");
        this.restClientBuilder = Objects.requireNonNull(restClientBuilder, "El RestClient.Builder no puede ser nulo");
        this.restClient = restClientBuilder
                .baseUrl(Objects.requireNonNull(ollamaBaseUrl, "La URL base de Ollama no puede ser nula"))
                .build();
        this.jsonSanitizer = new JsonSanitizer();
        this.modelName = Objects.requireNonNull(modelName, "El nombre del modelo Ollama no puede ser nulo");
    }

    public void reconfigure(String url, String model) {
        if (url != null && !url.isBlank()) {
            this.restClient = this.restClientBuilder
                    .baseUrl(url)
                    .build();
        }
        if (model != null && !model.isBlank()) {
            this.modelName = model;
        }
    }

    @Override
    public TranslationResult translate(TranslationChunk chunk) {
        Objects.requireNonNull(chunk, "El lote de traducci\u00f3n no puede ser nulo");

        String prompt = buildPrompt(chunk.translationsToTranslate());

        long backoffMs = INITIAL_BACKOFF_MS;

        for (int intento = 1; intento <= MAX_RETRIES; intento++) {
            LOGGER.log(System.Logger.Level.INFO, "Lote {0}, Modelo: {1}. Intento {2}/{3}.",
                    chunk.chunkId(), modelName, intento, MAX_RETRIES);

            try {
                OllamaResponse rawResponse = restClient.post()
                        .uri("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new OllamaRequest(modelName, prompt, false, "json", new OllamaOptions(0.1)))
                        .retrieve()
                        .body(OllamaResponse.class);

                if (rawResponse == null || rawResponse.response() == null) {
                    throw new RuntimeException("Ollama retorn\u00f3 una respuesta nula o vac\u00eda.");
                }

                String sanitized = jsonSanitizer.sanitize(rawResponse.response());
                Map<String, String> translatedMap = objectMapper.readValue(
                        sanitized, new TypeReference<Map<String, String>>() {}
                );

                LOGGER.log(System.Logger.Level.INFO, "Lote {0} completado: {1} claves traducidas.",
                        chunk.chunkId(), translatedMap.size());
                return new TranslationResult(chunk.chunkId(), translatedMap, Instant.now());

            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.ERROR, "Error en lote {0} (intento {1}/{2}): {3}",
                        chunk.chunkId(), intento, MAX_RETRIES, e.getMessage());

                if (intento == MAX_RETRIES) {
                    throw new RuntimeException("Fallo definitivo en lote " + chunk.chunkId()
                            + " tras " + MAX_RETRIES + " intentos", e);
                }

                LOGGER.log(System.Logger.Level.WARNING, "Reintentando en {0}ms...", backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }
        }

        throw new RuntimeException("No deber\u00eda llegar aqu\u00ed \u2014 todos los reintentos fallaron");
    }

    private String buildPrompt(Map<String, String> sourceTranslations) {
        try {
            String jsonInputString = objectMapper.writeValueAsString(sourceTranslations);

            return """
                   Eres un traductor experto en localizaci\u00f3n de mods de Minecraft, especializado en modpacks de rol (RPG).
                   Tu \u00fanica tarea es traducir mapas JSON de idiomas del ingl\u00e9s al espa\u00f1ol de Espa\u00f1a (es_es).

                   REGLAS ABSOLUTAS E INVIOLABLES:
                   1. Traduce \u00daNICAMENTE los valores del JSON. NUNCA debes alterar, traducir ni modificar las claves bajo ning\u00fan motivo.
                   2. Conserva exactamente todos los c\u00f3digos de color, formato e \u00edconos internos de Minecraft (por ejemplo: '\u00a7a', '\u00a7r', '%s', '%d', '%1$s', '{0}', '{1}', '\\n', etc.).
                   3. Mant\u00e9n un tono de fantas\u00eda/RPG medieval adaptado al estilo de juego de Minecraft. Usa terminolog\u00eda est\u00e1ndar en espa\u00f1ol para \u00edtems (por ejemplo, 'Iron' \u2192 'Hierro', 'Chest' \u2192 'Cofre').
                   4. PROTECCI\u00d3N DE NOMBRES DE MODS: NO traduzcas bajo ning\u00fan concepto los nombres propios de los mods (por ejemplo: 'Bosses of Mass Destruction', 'Simply Swords', 'BetterEnd', 'Mythic Upgrades', 'Archon', etc.). Deben permanecer en ingl\u00e9s para no romper documentaci\u00f3n ni referencias externas.
                   5. PROTECCI\u00d3N DE METADATOS: Si detectas que el valor corresponde a un men\u00fa t\u00e9cnico de configuraci\u00f3n o metadatos de ModMenu, trad\u00facelo de forma h\u00edbrida conservando el nombre del mod original (ej: 'Bosses of Mass Destruction Config' \u2192 'Configuraci\u00f3n de Bosses of Mass Destruction').
                   6. Debes responder estrictamente con un objeto JSON plano. No incluyas explicaciones, introducciones, saludos ni bloques de c\u00f3digo Markdown.

                   OBJETO JSON A TRADUCIR:
                   """ + jsonInputString;

        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Error al serializar el chunk a JSON para el Prompt", e);
            throw new RuntimeException("Fallo al construir el prompt de traducci\u00f3n", e);
        }
    }

    private record OllamaRequest(String model, String prompt, boolean stream, String format, OllamaOptions options) {}
    private record OllamaOptions(double temperature) {}
    private record OllamaResponse(String response) {}
}
