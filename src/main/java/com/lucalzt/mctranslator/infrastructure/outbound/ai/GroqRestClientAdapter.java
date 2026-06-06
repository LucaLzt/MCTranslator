package com.lucalzt.mctranslator.infrastructure.outbound.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.exception.ChunkFatalException;
import com.lucalzt.mctranslator.domain.exception.ChunkRetryableException;
import com.lucalzt.mctranslator.domain.exception.SessionFatalException;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptador de salida encargado de conectarse con la API de Groq en la nube.
 * * Versión de Grado de Producción con Cascada Dinámica y Pacing Adaptativo por Modelo.
 * * Rotación automática de modelos de respaldo para exprimir hasta 3,000,000 de tokens gratuitos por día.
 * * Utiliza registros de metadatos para optimizar la velocidad de red en base al RPM de cada LLM.
 * * Utiliza ApiKeyPoolManager para rotar llaves dinámicamente ante saturaciones.
 * * Aplica backoff exponencial y lógica de descarte de credenciales inválidas en caliente.
 */
@Component
public class GroqRestClientAdapter implements TranslationEnginePort {

    private static final System.Logger LOGGER = System.getLogger(GroqRestClientAdapter.class.getName());

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final long MAX_BACKOFF_MS = 16000L;
    private static final int ESTIMATED_TOKENS_PER_KEY = 400;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiKeyPoolManager apiKeyPool;
    private final JsonSanitizer jsonSanitizer;
    private final int totalKeysConfigured;

    private final List<ModelMetadata> modelCascade = List.of(
            new ModelMetadata("meta-llama/llama-4-scout-17b-16e-instruct", 30, 30, 4096, true, 500000),
            new ModelMetadata("qwen/qwen3-32b", 60, 5, 4096, false, 6000),
            new ModelMetadata("llama-3.1-8b-instant", 30, 10, 4096, true, 6000)
    );

    private final AtomicInteger activeModelIndex = new AtomicInteger(0);

    public GroqRestClientAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ApiKeyPoolManager apiKeyPool,
            @Value("${mctranslator.groq.url:https://api.groq.com/openai/v1}") String groqBaseUrl,
            @Value("${mctranslator.groq.keys:}") String rawKeys
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "El ObjectMapper de Jackson no puede ser nulo");
        this.apiKeyPool = Objects.requireNonNull(apiKeyPool, "El ApiKeyPoolManager no puede ser nulo");
        this.jsonSanitizer = new JsonSanitizer();
        this.restClient = restClientBuilder
                .baseUrl(Objects.requireNonNull(groqBaseUrl, "La URL base de Groq no puede ser nula"))
                .build();
        this.totalKeysConfigured = (rawKeys == null || rawKeys.isBlank()) ? 1 : rawKeys.split(",").length;
    }

    @Override
    public TranslationResult translate(TranslationChunk chunk) {
        Objects.requireNonNull(chunk, "El lote de traducción no puede ser nulo");

        Map<String, String> totalTranslations = chunk.translationsToTranslate();
        if (totalTranslations.isEmpty()) {
            return new TranslationResult(chunk.chunkId(), Collections.emptyMap(), Instant.now());
        }

        ModelMetadata activeModelMeta = getActiveModelMetadata();
        String activeModelName = activeModelMeta.id();
        int activeModelRpm = activeModelMeta.rpmLimit();
        int activeSubChunkSize = activeModelMeta.subChunkSize();

        List<Map<String, String>> subChunks = splitMap(totalTranslations, activeSubChunkSize);
        int totalSubChunks = subChunks.size();

        long pacingDelay = calculatePacingDelay(activeModelMeta, totalKeysConfigured);

        LOGGER.log(System.Logger.Level.INFO, "Lote {0} dividido en {1} sub-lotes. Modelo: {2} ({3} RPM, {4} claves/sub-lote, pausa {5}ms)",
                chunk.chunkId(), totalSubChunks, activeModelName, activeModelRpm, activeSubChunkSize, pacingDelay);
        Map<String, String> mergedResults = new ConcurrentHashMap<>();

        if (activeModelMeta.parallel()) {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < totalSubChunks; i++) {
                    final int subIdx = i;
                    final Map<String, String> subMap = subChunks.get(i);
                    final long totalPacingOffset = pacingDelay * i;

                    executor.submit(() -> {
                        try {
                            if (totalPacingOffset > 0) {
                                Thread.sleep(totalPacingOffset);
                            }
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        Map<String, String> translatedSubMap = translateSubChunkWithRetry(
                                chunk.chunkId(), subIdx, totalSubChunks, subMap);
                        mergedResults.putAll(translatedSubMap);
                    });
                }
            }
        } else {
            for (int i = 0; i < totalSubChunks; i++) {
                Map<String, String> subMap = subChunks.get(i);
                try {
                    if (pacingDelay * i > 0) {
                        Thread.sleep(pacingDelay * i);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                Map<String, String> translatedSubMap = translateSubChunkWithRetry(
                        chunk.chunkId(), i, totalSubChunks, subMap);
                mergedResults.putAll(translatedSubMap);
            }
        }

        LOGGER.log(System.Logger.Level.INFO, "Lote {0} completado: {1} claves traducidas.",
                chunk.chunkId(), mergedResults.size());

        return new TranslationResult(chunk.chunkId(), Map.copyOf(mergedResults), Instant.now());
    }

    private Map<String, String> translateSubChunkWithRetry(int parentChunkId, int subIdx, int totalSubChunks, Map<String, String> subMap) {
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int intento = 1; intento <= MAX_RETRIES; intento++) {
            ModelMetadata activeModelMeta = getActiveModelMetadata();
            String activeModel = activeModelMeta.id();
            String activeKey = apiKeyPool.next();
            int activeMaxTokens = activeModelMeta.maxTokens();

            GroqRequest payload = new GroqRequest(
                    activeModel,
                    List.of(
                            new Message("system", "Traduce el JSON de Minecraft de inglés a español (es_es). Conserva claves y códigos de formato intactos. Responde únicamente con el JSON."),
                            new Message("user", buildPrompt(subMap))
                    ),
                    0.0,
                    new ResponseFormat("json_object"),
                    activeMaxTokens
            );

            try {
                LOGGER.log(System.Logger.Level.DEBUG, "Sub-lote {0}/{1} (Lote {2}, Modelo: {3}). Intento {4}/{5}.",
                        subIdx + 1, totalSubChunks, parentChunkId, activeModel, intento, MAX_RETRIES);

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
                String errorBody = ex.getResponseBodyAsString();

                LOGGER.log(System.Logger.Level.WARNING, "Error HTTP {0} desde Groq. Cuerpo: {1}", statusCode, errorBody);

                switch (statusCode) {
                    case 429 -> {
                        if (errorBody.contains("tokens per day") || errorBody.contains("TPD")) {
                            LOGGER.log(System.Logger.Level.WARNING, "TPD agotado para el modelo: {0}. Rotando...", activeModel);
                            rotateModel(activeModel);
                        } else {
                            backoffMs = sleepAndCalculateBackoff(backoffMs, "Rate limit temporal (RPM/TPM).");
                        }
                    }
                    case 401, 403 -> apiKeyPool.markAsInvalid(activeKey);
                    case 400, 413, 422 -> {
                        if (errorBody.contains("json_validate_failed") || errorBody.contains("max completion tokens")) {
                            backoffMs = sleepAndCalculateBackoff(backoffMs, "Modelo agotó tokens de salida (json_validate_failed).");
                        } else {
                            throw new ChunkFatalException("Error fatal en sub-lote " + (subIdx + 1), parentChunkId, ex);
                        }
                    }
                    default -> backoffMs = sleepAndCalculateBackoff(backoffMs, "Error HTTP " + statusCode);
                }
            } catch (Exception e) {
                backoffMs = sleepAndCalculateBackoff(backoffMs, "Error de red: " + e.getClass().getSimpleName());
            }
        }

        throw new ChunkRetryableException("Se agotaron los reintentos en el sub-lote " + (subIdx + 1), parentChunkId);
    }

    private ModelMetadata getActiveModelMetadata() {
        int idx = activeModelIndex.get();
        if (idx >= modelCascade.size()) {
            throw new SessionFatalException("Todos los modelos de la cascada de Groq agotaron su cuota TPD diaria.");
        }
        return modelCascade.get(idx);
    }

    private String getActiveModel() {
        return getActiveModelMetadata().id();
    }

    private void rotateModel(String modelThatFailed) {
        synchronized (activeModelIndex) {
            int currentIdx = activeModelIndex.get();
            if (currentIdx < modelCascade.size() && modelCascade.get(currentIdx).id().equals(modelThatFailed)) {
                int nextIdx = currentIdx + 1;
                activeModelIndex.set(nextIdx);
                if (nextIdx < modelCascade.size()) {
                    ModelMetadata nextMeta = modelCascade.get(nextIdx);
                    long nextPacing = calculatePacingDelay(nextMeta, totalKeysConfigured);
                    LOGGER.log(System.Logger.Level.WARNING, "==================================================================");
                    LOGGER.log(System.Logger.Level.WARNING, "Rotando modelo de Groq hacia: {0} ({1} RPM, {2} claves/sub-lote, pausa {3}ms)", nextMeta.id(), nextMeta.rpmLimit(), nextMeta.subChunkSize(), nextPacing);
                    LOGGER.log(System.Logger.Level.WARNING, "==================================================================");
                } else {
                    LOGGER.log(System.Logger.Level.ERROR, "Todos los modelos de Groq agotaron su cuota diaria.");
                }
            }
        }
    }

    private long calculatePacingDelay(ModelMetadata meta, int totalKeys) {
        long rpmDelay = (60000L / meta.rpmLimit()) / totalKeys;
        long tpmDelay = (ESTIMATED_TOKENS_PER_KEY * meta.subChunkSize() * 60000L) / meta.tpmLimit();
        return Math.max(rpmDelay, tpmDelay);
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
        LOGGER.log(System.Logger.Level.WARNING, "{0} — pausa {1}ms", reason, currentBackoffMs);
        try {
            Thread.sleep(currentBackoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }
        return Math.min(currentBackoffMs * 2, MAX_BACKOFF_MS);
    }

    private String buildPrompt(Map<String, String> sourceTranslations) {
        try {
            return objectMapper.writeValueAsString(sourceTranslations);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // --- Registros de metadatos internos de infraestructura ---
    private record ModelMetadata(String id, int rpmLimit, int subChunkSize, int maxTokens, boolean parallel, int tpmLimit) {}
    private record GroqRequest(String model, List<Message> messages, double temperature, ResponseFormat response_format, int max_tokens) {}
    private record Message(String role, String content) {}
    private record ResponseFormat(String type) {}
    private record GroqResponse(List<Choice> choices) {}
    private record Choice(Message message) {}
}
