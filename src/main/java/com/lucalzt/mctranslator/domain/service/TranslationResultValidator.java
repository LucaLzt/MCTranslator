package com.lucalzt.mctranslator.domain.service;

import com.lucalzt.mctranslator.domain.exception.ChunkRetryableException;
import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validador de negocio encargado de garantizar la simetría e integridad de las claves de traducción.
 * * Claves con valores vacíos se loguean como advertencia y se toleran (no matan el chunk).
 * * Solo se lanza ChunkRetryableException si todas las claves del chunk fallaron.
 */
public class TranslationResultValidator {

    private static final System.Logger LOGGER = System.getLogger(TranslationResultValidator.class.getName());

    /**
     * Compara un lote de traducción (chunk) con su resultado correspondiente.
     *
     * @param chunk  El lote original con las claves de entrada.
     * @param result El resultado obtenido y parseado.
     * @throws ChunkRetryableException Si todas las claves del chunk fallaron.
     */
    public void validate(TranslationChunk chunk, TranslationResult result) {
        Objects.requireNonNull(chunk, "El lote de traducción original no puede ser nulo");
        Objects.requireNonNull(result, "El resultado de la traducción no puede ser nulo");

        Map<String, String> requested = chunk.translationsToTranslate();
        Map<String, String> translated = result.translatedTranslations();

        List<String> invalidKeys = new ArrayList<>();

        for (String key : requested.keySet()) {
            if (!translated.containsKey(key)) {
                LOGGER.log(System.Logger.Level.WARNING, "Clave omitida por el LLM: {0}", key);
                invalidKeys.add(key);
                continue;
            }

            String translatedValue = translated.get(key);
            if (translatedValue == null || translatedValue.isBlank()) {
                LOGGER.log(System.Logger.Level.WARNING, "Traducción vacía para la clave: {0}", key);
                invalidKeys.add(key);
            }
        }

        if (!invalidKeys.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "{0} de {1} claves tienen traducciones inválidas en el lote {2}",
                    invalidKeys.size(), requested.size(), chunk.chunkId());
        }

        if (invalidKeys.size() == requested.size()) {
            throw new ChunkRetryableException(
                    "Todas las " + requested.size() + " claves del lote " + chunk.chunkId()
                            + " tienen traducciones inválidas o ausentes",
                    chunk.chunkId()
            );
        }
    }
}
