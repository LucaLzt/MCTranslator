package com.lucalzt.mctranslator.domain.service;

import com.lucalzt.mctranslator.domain.model.TranslationChunk;

import java.util.*;

/**
 * Servicio de negocio encargado de dividir un conjunto grande de traducciones
 * en lotes estructurados de un tamaño máximo definido para cumplir con los límites de cuota (Rate Limits).
 */
public class ChunkingService {

    /**
     * Divide un mapa de traducciones pendientes en una lista de lotes (chunks).
     * Mantiene el orden de inserción de las claves.
     *
     * @param translations El mapa completo de claves y valores a segmentar.
     * @param maxChunkSize El límite máximo de claves por cada lote individual.
     * @return Una lista inmutable de TranslationChunks listos para el pipeline.
     */
    public List<TranslationChunk> split(Map<String, String> translations, int maxChunkSize) {
        if (translations == null || translations.isEmpty()) {
            return Collections.emptyList();
        }

        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("El tamaño máximo del lote debe ser estrictamente mayor a cero");
        }

        List<TranslationChunk> chunks = new ArrayList<>();
        Map<String, String> currentMap = new LinkedHashMap<>();
        int chunkId = 0;

        for (Map.Entry<String, String> entry : translations.entrySet()) {
            currentMap.put(entry.getKey(), entry.getValue());

            if (currentMap.size() >= maxChunkSize) {
                chunks.add(new TranslationChunk(chunkId++, currentMap));
                currentMap = new LinkedHashMap<>();
            }
        }

        // Añado el remanente final si existe
        if (!currentMap.isEmpty()) {
            chunks.add(new TranslationChunk(chunkId, currentMap));
        }

        return List.copyOf(chunks);
    }
}
