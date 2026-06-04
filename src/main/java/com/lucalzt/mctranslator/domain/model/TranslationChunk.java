package com.lucalzt.mctranslator.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * Representa un segmento o lote delimitado de claves y valores de traducción.
 * Este objeto se diseña específicamente para respetar las cuotas y límites de tokens de los LLMs.
 */
public record TranslationChunk(
        int chunkId,
        Map<String, String> translationsToTranslate
) {
    /**
     * Constructor que garantiza la consistencia del chunk.
     */
    public TranslationChunk {
        Objects.requireNonNull(translationsToTranslate, "El mapa de traducciones del lote no puede ser nulo");

        if (translationsToTranslate.isEmpty()) {
            throw new IllegalArgumentException("No se puede crear un chunk de traducción vacío");
        }
        if (chunkId < 0) {
            throw new IllegalArgumentException("El identificador del chunk no puede ser negativo");
        }

        translationsToTranslate = Map.copyOf(translationsToTranslate);
    }

    /**
     * Obtiene el volumen total de entradas a traducir dentro de este lote.
     *
     * @return Cantidad de pares clave-valor contenidos en el chunk.
     */
    public int size() {
        return translationsToTranslate.size();
    }
}
