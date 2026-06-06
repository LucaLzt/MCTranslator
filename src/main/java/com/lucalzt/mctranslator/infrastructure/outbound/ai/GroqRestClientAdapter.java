package com.lucalzt.mctranslator.infrastructure.outbound.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.exception.ChunkFatalException;
import com.lucalzt.mctranslator.domain.exception.ChunkRetryableException;
import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.domain.service.JsonSanitizer;
import com.lucalzt.mctranslator.infrastructure.outbound.ai.pool.ApiKeyPoolManager;
import com.lucalzt.mctranslator.ports.outbound.TranslationEnginePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adaptador de salida encargado de conectarse con la API de Groq en la nube.
 * * Implementa TranslationEnginePort.
 * * Utiliza ApiKeyPoolManager para rotar llaves dinámicamente ante saturaciones.
 * * Aplica backoff exponencial y lógica de descarte de credenciales inválidas en caliente.
 */
@Component
public class GroqRestClientAdapter implements TranslationEnginePort {

    private static final System.Logger LOGGER = System.getLogger(GroqRestClientAdapter.class.getName());

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final long MAX_BACKOFF_MS = 16000L;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiKeyPoolManager apiKeyPool;
    private final JsonSanitizer jsonSanitizer;

    @Value("${mctranslator.groq.url:[https://api.groq.com/openai/v1](https://api.groq.com/openai/v1)}")
    private String groqBaseUrl;

    @Value("${mctranslator.groq.model:mixtral-8x7b-32768}")
    private String modelName;

    /**
     * Construye el adaptador inyectando las herramientas de red, serialización y el pool de credenciales.
     */
    public GroqRestClientAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ApiKeyPoolManager apiKeyPool
    ) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = Objects.requireNonNull(objectMapper, "El ObjectMapper de Jackson no puede ser nulo");
        this.apiKeyPool = Objects.requireNonNull(apiKeyPool, "El ApiKeyPoolManager no puede ser nulo");
        this.jsonSanitizer = new JsonSanitizer();
    }

    @Override
    public TranslationResult translate(TranslationChunk chunk) {
        Objects.requireNonNull(chunk, "El lote de traducción no puede ser nulo");

        LOGGER.log(System.Logger.Level.INFO, "Iniciando traducción en Groq con modelo: '{0}'. Lote ID: {1}", modelName, chunk.chunkId());

        String prompt = buildPrompt(chunk.translationsToTranslate());

        // Estructura de payload compatible con la API de chat de OpenAI/Groq
        GroqRequest payload = new GroqRequest(
                modelName,
                List.of(
                        new Message(
                                "system",
                                """
                                    Eres un traductor de localización de Minecraft ultra preciso. Debes responder 
                                    exclusivamente con un mapa de JSON válido
                                    """),
                        new Message(
                                "user",
                                prompt
                        )
                ),
                0.0, // Temperatura 0.1 para mitigar alucinaciones en las traducciones
                new ResponseFormat("json_object") // Fuerzo el modo JSON estricto
        );

        long backoffMs = INITIAL_BACKOFF_MS;

        for (int intento = 1; intento <= MAX_RETRIES; intento++) {
            String activeKey = apiKeyPool.next();

            try {
                LOGGER.log(System.Logger.Level.DEBUG, "Lanzando petición REST a Groq. Intento {0}/{1}. Lote ID: {2}",
                        intento, MAX_RETRIES, chunk.chunkId());

                GroqResponse response = restClient.post()
                        .uri(groqBaseUrl + "/chat/completions")
                        .header("Authorization", "Bearer " + activeKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(GroqResponse.class);

                if (response == null || response.choices() == null || response.choices().isEmpty()) {
                    throw new RuntimeException("La API de Groq retornó una estructura de respuesta vacía o incompleta.");
                }

                // Obtengo el texto crudo de la respuesta del modelo
                String rawTextResponse = response.choices().getFirst().message().content();

                // 1. Sanitizo respuesta de la IA
                String cleanJson = jsonSanitizer.sanitize(rawTextResponse);

                // 2. Mapeo a un mapa estructurado
                Map<String, String> translatedMap = objectMapper.readValue(
                        cleanJson,
                        new TypeReference<Map<String, String>>() {}
                );

                // Si llegamos acá, significa que salió bien. Reseteamos los fallos acumulados de la llave activa.
                apiKeyPool.resetFailures(activeKey);

                LOGGER.log(System.Logger.Level.INFO, "Lote ID {0} traducido con éxito por Groq en el intento {1}.",
                        chunk.chunkId(), intento);

                return new TranslationResult(chunk.chunkId(), translatedMap, Instant.now());

            } catch (HttpStatusCodeException ex) {
                int statusCode = ex.getStatusCode().value();
                LOGGER.log(System.Logger.Level.WARNING, "Error HTTP {0} recibido de la API de Groq en el intento {1}.",
                        statusCode, intento);

                switch (statusCode) {
                    case 429 -> {
                        apiKeyPool.recordFailure(activeKey);
                        backoffMs = sleepAndCalculateBackoff(backoffMs, "Límite de tasa (Rate Limit) alcanzado (HTTP 429).");
                    }
                    case 401, 403 -> {
                        apiKeyPool.markAsInvalid(activeKey);
                        LOGGER.log(System.Logger.Level.WARNING, "Llave rechazada. Saltando a la siguiente llave disponible de inmediato.");
                    }
                    case 400, 413, 422 -> {
                        LOGGER.log(System.Logger.Level.ERROR, "Fallo de validación irrecuperable en el lote. Detallando error: {0}", ex.getResponseBodyAsString());
                        throw new ChunkFatalException("La API de Groq rechazó la estructura del lote enviado por ser semánticamente inválido o exceder límites.", chunk.chunkId(), ex);
                    }
                    default -> backoffMs = sleepAndCalculateBackoff(backoffMs, "Error de red intermitente o caída del servidor de Groq (HTTP " + statusCode + ").");
                }

            } catch (Exception e) {
                // Captura de timeouts o fallos de lectura de JSON
                LOGGER.log(System.Logger.Level.WARNING, "Error inusual durante la llamada en el intento {0}: {1}", intento, e.getMessage());
                backoffMs = sleepAndCalculateBackoff(backoffMs, "Excepción de comunicación de red: " + e.getClass().getSimpleName());
            }
        }

        // Si agotamos todos los reintentos para este lote, levantamos un error reintentable para que el orquestador lo gestione
        throw new ChunkRetryableException("Se agotaron todos los reintentos de comunicación con la API de Groq sin éxito estructural.", chunk.chunkId());
    }

    private long sleepAndCalculateBackoff(long currentBackoffMs, String reason) {
        LOGGER.log(System.Logger.Level.WARNING, "{0} Durmiendo el hilo {1}ms antes del reintento por resiliencia...",
                reason, currentBackoffMs);
        try {
            Thread.sleep(currentBackoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("El hilo de ejecución fue interrumpido durante la pausa de resiliencia", ie);
        }
        return Math.min(currentBackoffMs * 2, MAX_BACKOFF_MS);
    }

    private String buildPrompt(Map<String, String> sourceTranslations) {
        try {
            String jsonInput = objectMapper.writeValueAsString(sourceTranslations);
            return """
                   Traduce este mapa JSON de localización de Minecraft al español (es_es).
                   Requisitos no negociables:
                   1. Conserva todas las claves intactas de forma exacta.
                   2. Traduce únicamente los valores lingüísticos.
                   3. Respeta todos los códigos de formato e íconos (§a, §r, %s, %d, etc.).
                   4. Responde con un objeto JSON válido. No agregues explicaciones fuera de él.
                   
                   OBJETO A TRADUCIR:
                   """ + jsonInput;
        } catch (Exception e) {
            throw new RuntimeException("Fallo al construir el prompt de Groq", e);
        }
    }

    // --- Registros internos de deserialización de la API de Groq ---
    private record GroqRequest(String model, List<Message> messages, double temperature, ResponseFormat response_format) {}
    private record Message(String role, String content) {}
    private record ResponseFormat(String type) {}
    private record GroqResponse(List<Choice> choices) {}
    private record Choice(Message message) {}
}
