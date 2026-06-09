package com.lucalzt.mctranslator.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * Representa los textos extraídos de un sistema de misiones listos para traducir.
 */
public record QuestData(
        QuestSystemType systemType,
        Map<String, String> entries,
        byte[] rawPayload
) {
    public QuestData {
        Objects.requireNonNull(systemType, "El tipo de sistema de quests no puede ser nulo");
        Objects.requireNonNull(entries, "El mapa de entradas no puede ser nulo");
        entries = Map.copyOf(entries);
    }
}
