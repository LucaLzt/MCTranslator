package com.lucalzt.mctranslator.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Representa la respuesta de traducción ya procesada, sanitizada y lista para persistirse.
 * Contiene el mapa de traducciones en el idioma destino y está estrictamente asociada al chunk que la originó.
 */
public record TranslationResult(
        int chunkId,
        Map<String, String> translatedTranslations,
        Instant timestamp
) {

    /**
     * Constructor para calidad e inmunizar el resultado.
     */
    public TranslationResult {
        Objects.requireNonNull(translatedTranslations, "El mapa de traducciones resultantes no puede ser nulo");
        Objects.requireNonNull(timestamp, "El registro de tiempo (timestamp) no puede ser nulo");

        if (chunkId < 0) {
            throw new IllegalArgumentException("El identificador del chunk no puede ser negativo");
        }

        translatedTranslations = Map.copyOf(translatedTranslations);
    }
}
