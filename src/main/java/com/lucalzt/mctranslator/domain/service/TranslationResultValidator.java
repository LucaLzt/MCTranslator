package com.lucalzt.mctranslator.domain.service;

import com.lucalzt.mctranslator.domain.exception.ChunkRetryableException;
import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;

import java.util.Map;
import java.util.Objects;

/**
 * Validador de negocio encargado de garantizar la simetría e integridad de las claves de traducción.
 * * Verifica que el mapa devuelto por la IA contenga exactamente las mismas claves que se le enviaron
 * y que no existan valores vacíos, nulos o corruptos.
 */
public class TranslationResultValidator {

    /**
     * Compara un lote de traducción (chunk) con su resultado correspondiente.
     *
     * @param chunk  El lote original con las claves de entrada.
     * @param result El resultado obtenido y parseado.
     * @throws ChunkRetryableException Si se detecta asimetría en las claves o traducciones vacías.
     */
    public void validate(TranslationChunk chunk, TranslationResult result) {
        Objects.requireNonNull(chunk, "El lote de traducción original no puede ser nulo");
        Objects.requireNonNull(result, "El resultado de la traducción no puede ser nulo");

        Map<String, String> requested = chunk.translationsToTranslate();
        Map<String, String> translated = result.translatedTranslations();

        for (String key : requested.keySet()) {
            // Verifico si el LLM omitió la clave de la respuesta
            if (!translated.containsKey(key)) {
                throw new ChunkRetryableException(
                        String.format("La clave requerida '%s' fue omitida en la respuesta de traducción", key),
                        chunk.chunkId()
                );
            }

            // Verifico si el valor retornado no es nulo, vacío o que no contenga espacios en blanco
            String translatedValue = translated.get(key);
            if (translatedValue == null || translatedValue.isBlank()) {
                throw new ChunkRetryableException(
                        String.format("La clave '%s' contiene una traducción nula, vacía o inválida", key),
                        chunk.chunkId()
                );
            }
        }
    }
}
