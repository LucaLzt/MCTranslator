package com.lucalzt.mctranslator.infrastructure.outbound.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.exception.ChunkFatalException;
import com.lucalzt.mctranslator.domain.exception.ChunkRetryableException;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;

@Component
public class GroqRestClientAdapter implements TranslationEnginePort {

    private static final System.Logger LOGGER = System.getLogger(GroqRestClientAdapter.class.getName());
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final long MAX_BACKOFF_MS = 16000L;
    private static final int ESTIMATED_TOKENS_PER_KEY = 400;

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final JsonSanitizer jsonSanitizer;

    private RestClient restClient;
    private String apiKey;
    private EngineRegistry engineRegistry;

    private volatile ModelMetadata currentModel = new ModelMetadata(
            "meta-llama/llama-4-scout-17b-16e-instruct", 30, 8192, 500000);

    @Autowired
    public void setEngineRegistry(EngineRegistry engineRegistry) {
        this.engineRegistry = engineRegistry;
    }

    @PostConstruct
    public void register() {
        if (engineRegistry != null) {
            engineRegistry.register("groq", this);
        }
    }

    public GroqRestClientAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${mctranslator.groq.url:https://api.groq.com/openai/v1}") String groqBaseUrl,
            @Value("${mctranslator.groq.key:}") String rawKey
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "El ObjectMapper de Jackson no puede ser nulo");
        this.restClientBuilder = Objects.requireNonNull(restClientBuilder, "El RestClient.Builder no puede ser nulo");
        this.jsonSanitizer = new JsonSanitizer();
        this.restClient = restClientBuilder
                .baseUrl(Objects.requireNonNull(groqBaseUrl, "La URL base de Groq no puede ser nula"))
                .build();
        this.apiKey = (rawKey == null || rawKey.isBlank()) ? "DUMMY_KEY" : rawKey;
    }

    public void reconfigure(String url, String model, String key, Integer rpm, Integer maxTokens, Integer tpm) {
        if (url != null && !url.isBlank()) {
            this.restClient = this.restClientBuilder
                    .baseUrl(url)
                    .build();
        }
        if (model != null && !model.isBlank()) {
            this.currentModel = new ModelMetadata(
                    model,
                    rpm != null ? rpm : 30,
                    maxTokens != null ? maxTokens : 8192,
                    tpm != null ? tpm : 500000
            );
        }
        if (key != null && !key.isBlank()) {
            this.apiKey = key;
        }
    }

    @Override
    public TranslationResult translate(TranslationChunk chunk) {
        Objects.requireNonNull(chunk, "El lote de traducción no puede ser nulo");

        Map<String, String> totalTranslations = chunk.translationsToTranslate();
        if (totalTranslations.isEmpty()) {
            return new TranslationResult(chunk.chunkId(), Collections.emptyMap(), Instant.now());
        }

        String modelName = currentModel.id();
        int modelRpm = currentModel.rpmLimit();
        int requestKeyCount = totalTranslations.size();

        long pacingDelay = calculatePacingDelay(currentModel, requestKeyCount);
        LOGGER.log(System.Logger.Level.INFO, "Lote {0}: {1} claves, modelo {2} ({3} RPM, pausa {4}ms)",
                chunk.chunkId(), requestKeyCount, modelName, modelRpm, pacingDelay);

        try {
            Thread.sleep(pacingDelay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        Map<String, String> translated = translateWithRetry(chunk.chunkId(), totalTranslations);
        LOGGER.log(System.Logger.Level.INFO, "Lote {0} completado: {1} claves traducidas.",
                chunk.chunkId(), translated.size());

        return new TranslationResult(chunk.chunkId(), Map.copyOf(translated), Instant.now());
    }

    private Map<String, String> translateWithRetry(int chunkId, Map<String, String> translations) {
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int intento = 1; intento <= MAX_RETRIES; intento++) {
            String activeModel = currentModel.id();
            int activeMaxTokens = currentModel.maxTokens();

            GroqRequest payload = new GroqRequest(
                    activeModel,
                    List.of(
                            new Message("system", "Traduce el JSON de Minecraft de inglés a español (es_es). Conserva claves y códigos de formato intactos. Responde únicamente con el JSON."),
                            new Message("user", buildPrompt(translations))
                    ),
                    0.0,
                    new ResponseFormat("json_object"),
                    activeMaxTokens
            );

            try {
                LOGGER.log(System.Logger.Level.DEBUG, "Lote {0}, Modelo: {1}. Intento {2}/{3}.",
                        chunkId, activeModel, intento, MAX_RETRIES);

                GroqResponse response = restClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(GroqResponse.class);

                if (response == null || response.choices() == null || response.choices().isEmpty()) {
                    throw new RuntimeException("La API de Groq retornó una respuesta vacía.");
                }

                String rawTextResponse = response.choices().getFirst().message().content();
                String cleanJson = jsonSanitizer.sanitize(rawTextResponse);

                return objectMapper.readValue(
                        cleanJson,
                        new TypeReference<Map<String, String>>() {}
                );
            } catch (HttpStatusCodeException ex) {
                int statusCode = ex.getStatusCode().value();
                String errorBody = ex.getResponseBodyAsString();

                LOGGER.log(System.Logger.Level.WARNING, "Error HTTP {0} desde Groq. Cuerpo: {1}", statusCode, errorBody);

                switch (statusCode) {
                    case 429 -> {
                        backoffMs = sleepAndCalculateBackoff(backoffMs, "Rate limit (RPM/TPM/TPD).");
                    }
                    case 401, 403 -> {
                        LOGGER.log(System.Logger.Level.WARNING, "Llave de API rechazada (HTTP {0}).", statusCode);
                    }
                    case 400, 413, 422 -> {
                        if (errorBody.contains("json_validate_failed") || errorBody.contains("max completion tokens")) {
                            backoffMs = sleepAndCalculateBackoff(backoffMs, "Modelo agotó tokens de salida (json_validate_failed).");
                        } else {
                            throw new ChunkFatalException("Error fatal en lote " + chunkId, chunkId, ex);
                        }
                    }
                    default -> {
                        backoffMs = sleepAndCalculateBackoff(backoffMs, "Error HTTP " + statusCode);
                    }
                }
            } catch (Exception e) {
                backoffMs = sleepAndCalculateBackoff(backoffMs, "Error de red: " + e.getClass().getSimpleName());
            }
        }

        throw new ChunkRetryableException("Se agotaron los reintentos para el lote " + chunkId, chunkId);
    }

    private long calculatePacingDelay(ModelMetadata meta, int requestKeyCount) {
        long rpmDelay = 60000L / meta.rpmLimit();
        long tpmDelay = (ESTIMATED_TOKENS_PER_KEY * requestKeyCount * 60000L) / meta.tpmLimit();
        return Math.max(rpmDelay, tpmDelay);
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

    private record ModelMetadata(String id, int rpmLimit, int maxTokens, int tpmLimit) {}
    private record GroqRequest(String model, List<Message> messages, double temperature, ResponseFormat response_format, int max_tokens) {}
    private record Message(String role, String content) {}
    private record ResponseFormat(String type) {}
    private record GroqResponse(List<Choice> choices) {}
    private record Choice(Message message) {}
}
