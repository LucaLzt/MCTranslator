package com.lucalzt.mctranslator.domain.model;

import java.time.Instant;
import java.util.Objects;

public record GlossaryEntry(
        String termEn,
        String termEs,
        String source,
        int occurrences,
        Instant firstSeen
) {
    public GlossaryEntry {
        Objects.requireNonNull(termEn, "El término en inglés no puede ser nulo");
        Objects.requireNonNull(termEs, "La traducción al español no puede ser nula");
        Objects.requireNonNull(source, "La fuente del término no puede ser nula");
        Objects.requireNonNull(firstSeen, "La marca temporal firstSeen no puede ser nula");

        if (termEn.isBlank()) {
            throw new IllegalArgumentException("El término en inglés no puede estar vacío");
        }
        if (termEs.isBlank()) {
            throw new IllegalArgumentException("La traducción al español no puede estar vacía");
        }
        if (source.isBlank()) {
            throw new IllegalArgumentException("La fuente del término no puede estar vacía");
        }
        if (occurrences < 1) {
            throw new IllegalArgumentException("El contador de ocurrencias debe ser al menos 1");
        }
    }

    public GlossaryEntry incrementOccurrences() {
        return new GlossaryEntry(termEn, termEs, source, occurrences + 1, firstSeen);
    }
}
