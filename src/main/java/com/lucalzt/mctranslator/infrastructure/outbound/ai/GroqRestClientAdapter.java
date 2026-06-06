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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Adaptador de salida encargado de conectarse con la API de Groq en la nube.
 * * Implementa TranslationEnginePort.
 * * Utiliza ApiKeyPoolManager para rotar llaves dinámicamente ante saturaciones.
 * * Aplica backoff exponencial y lógica de descarte de credenciales inválidas en caliente.
 * * Emplea hilos virtuales para maximizar la velocidad de la red sin bloqueos.
 */
@Component
public class GroqRestClientAdapter implements TranslationEnginePort {

    private static final System.Logger LOGGER = System.getLogger(GroqRestClientAdapter.class.getName());

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final long MAX_BACKOFF_MS = 16000L;

    // Cuota de velocidad del modelo en el plan gratuito
    private static final int MODEL_MAX_RPM = 30;

    // Fraccionador de sub-lotes óptimo
    private static final int PARALLEL_SUB_CHUNK_SIZE = 25;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiKeyPoolManager apiKeyPool;
    private final JsonSanitizer jsonSanitizer;
    private final String modelName;
    private final int totalKeysConfigured;

    public GroqRestClientAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ApiKeyPoolManager apiKeyPool,
            @Value("${mctranslator.groq.url:https://api.groq.com/openai/v1}") String groqBaseUrl,
            @Value("${mctranslator.groq.model:meta-llama/llama-4-scout-17b-16e-instruct}") String modelName,
            @Value("${mctranslator.groq.keys:}") String rawKeys
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "El ObjectMapper de Jackson no puede ser nulo");
        this.apiKeyPool = Objects.requireNonNull(apiKeyPool, "El ApiKeyPoolManager no puede ser nulo");
        this.modelName = Objects.requireNonNull(modelName, "El nombre del modelo no puede ser nulo");
        this.jsonSanitizer = new JsonSanitizer();
        this.restClient = restClientBuilder
                .baseUrl(Objects.requireNonNull(groqBaseUrl, "La URL base de Groq no puede ser nula"))
                .build();
        this.totalKeysConfigured = (rawKeys == null || rawKeys.isBlank()) ? 1 : rawKeys.split(",").length;
    }

    /**
     * Construye el adaptador inyectando las herramientas de red, serialización y el pool de credenciales.
     */
    @Override
    public TranslationResult translate(TranslationChunk chunk) {
        Objects.requireNonNull(chunk, "El lote de traducción no puede ser nulo");

        Map<String, String> totalTranslations = chunk.translationsToTranslate();
        if (totalTranslations.isEmpty()) {
            return new TranslationResult((chunk.chunkId()), Collections.emptyMap(), Instant.now());
        }

        // Divido el chunk actual en mapas más pequeños para las traducciones simultáneas
        List<Map<String, String>> subChunks = splitMap(totalTranslations, PARALLEL_SUB_CHUNK_SIZE);
        LOGGER.log(System.Logger.Level.INFO, "Paralelizando Lote ID: {0} en {1} sub-peticiones de red vía hilos virtuales",
                chunk.chunkId(), subChunks.size());

        Map<String, String> mergedResults = new ConcurrentHashMap<>();

        // Creo un ejecutor de hilos virtuales para enviar las peticiones
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < subChunks.size(); i++) {
                final int subId = i;
                final Map<String, String> subMap = subChunks.get(i);

                // Pacing Proactivo: espacia las requests para nunca superar las RPM combinadas del plan
                long pacingDelay = (60000L / MODEL_MAX_RPM) / totalKeysConfigured;
                long totalPacingOffset = pacingDelay * i;

                executor.submit(() -> {
                    try {
                        if (totalPacingOffset > 0) {
                            Thread.sleep(totalPacingOffset);
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    Map<String, String> translatedSubMap = translateSubChunkWithRetry(chunk.chunkId(), subId, subMap);
                    mergedResults.putAll(translatedSubMap);
                });
            }
        }

        LOGGER.log(System.Logger.Level.INFO, "Traducción paralela completa para Lote ID: {0}. Fusionadas {1} claves traducidas.",
                chunk.chunkId(), mergedResults.size());

        return new TranslationResult(chunk.chunkId(), Map.copyOf(mergedResults), Instant.now());
    }

    private Map<String, String> translateSubChunkWithRetry(int parentChunkId, int subId, Map<String, String> subMap) {
        String prompt = buildPrompt(subMap);
        GroqRequest payload = new GroqRequest(
                modelName,
                List.of(
                        new Message("system", "Eres un traductor de localización de Minecraft ultra preciso. Debes responder exclusivamente con un mapa de JSON válido."),
                        new Message("user", prompt)
                ),
                0.0,
                new ResponseFormat("json_object")
        );

        long backoffMs = INITIAL_BACKOFF_MS;

        for (int intento = 1; intento <= MAX_RETRIES; intento++) {
            // apiKeyPool.next() es sincronizado, por lo que su consumo es 100% seguro desde hilos concurrentes
            String activeKey = apiKeyPool.next();

            try {
                LOGGER.log(System.Logger.Level.DEBUG, "Sub-lote {0}/{1}. Lanzando petición REST a Groq. Intento {2}/{3}.",
                        parentChunkId, subId, intento, MAX_RETRIES);

                GroqResponse response = restClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + activeKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(GroqResponse.class);

                if (response == null || response.choices() == null || response.choices().isEmpty()) {
                    throw new RuntimeException("La API de Groq retornó una respuesta vacía.");
                }

                String rawTextResponse = response.choices().getFirst().message().content();
                String cleanJson = jsonSanitizer.sanitize(rawTextResponse);

                Map<String, String> translatedMap = objectMapper.readValue(
                        cleanJson,
                        new TypeReference<Map<String, String>>() {}
                );

                apiKeyPool.resetFailures(activeKey);
                return translatedMap;
            } catch (HttpStatusCodeException ex) {
                int statusCode = ex.getStatusCode().value();
                switch (statusCode) {
                    case 429 -> {
                        apiKeyPool.recordFailure(activeKey);
                        backoffMs = sleepAndCalculateBackoff(backoffMs, "Límite de tasa (Rate Limit) HTTP 429.");
                    }
                    case 401, 403 -> {
                        apiKeyPool.markAsInvalid(activeKey);
                    }
                    case 400, 413, 422 -> {
                        throw new ChunkFatalException("Error fatal en sub-lote " + subId, parentChunkId, ex);
                    }
                    default -> {
                        backoffMs = sleepAndCalculateBackoff(backoffMs, "Error HTTP " + statusCode);
                    }
                }
            } catch (Exception e) {
                backoffMs = sleepAndCalculateBackoff(backoffMs, "Error de comunicación: " + e.getClass().getSimpleName());
            }
        }

        throw new ChunkRetryableException("Se agotaron los reintentos en el sub-lote " + subId, parentChunkId);
    }

    private List<Map<String, String>> splitMap(Map<String, String> originalMap, int size) {
        List<Map<String, String>> list = new ArrayList<>();
        Map<String, String> current = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : originalMap.entrySet()) {
            current.put(entry.getKey(), entry.getValue());
            if (current.size() >= size) {
                list.add(current);
                current = new LinkedHashMap<>();
            }
        }
        if (!current.isEmpty()) {
            list.add(current);
        }
        return list;
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
