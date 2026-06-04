package com.lucalzt.mctranslator.domain.service;

import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChunkingService - splitting translations into chunks")
class ChunkingServiceTest {

    private final ChunkingService service = new ChunkingService();

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("returns empty list when map is null")
        void nullMap_returnsEmptyList() {
            List<TranslationChunk> result = service.split(null, 5);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty list when map is empty")
        void emptyMap_returnsEmptyList() {
            List<TranslationChunk> result = service.split(Map.of(), 5);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns single chunk when map is smaller than maxChunkSize")
        void mapSmallerThanMaxChunkSize_returnsSingleChunk() {
            Map<String, String> map = Map.of("k1", "v1", "k2", "v2", "k3", "v3");
            List<TranslationChunk> result = service.split(map, 5);
            assertEquals(1, result.size());
            assertEquals(0, result.getFirst().chunkId());
            assertEquals(3, result.getFirst().size());
        }

        @Test
        @DisplayName("returns single chunk when map equals maxChunkSize")
        void mapEqualToMaxChunkSize_returnsSingleChunk() {
            Map<String, String> map = Map.of("k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4", "k5", "v5");
            List<TranslationChunk> result = service.split(map, 5);
            assertEquals(1, result.size());
            assertEquals(0, result.getFirst().chunkId());
            assertEquals(5, result.getFirst().size());
        }

        @Test
        @DisplayName("returns full chunks when map is exact multiple")
        void mapExactMultiple_returnsMultipleFullChunks() {
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 1; i <= 10; i++) {
                map.put("k" + i, "v" + i);
            }
            List<TranslationChunk> result = service.split(map, 5);
            assertEquals(2, result.size());
            assertEquals(0, result.get(0).chunkId());
            assertEquals(5, result.get(0).size());
            assertEquals(1, result.get(1).chunkId());
            assertEquals(5, result.get(1).size());
        }

        @Test
        @DisplayName("returns full chunks with remainder when map is not exact multiple")
        void mapNotExactMultiple_returnsChunksWithRemainder() {
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 1; i <= 7; i++) {
                map.put("k" + i, "v" + i);
            }
            List<TranslationChunk> result = service.split(map, 3);
            assertEquals(3, result.size());
            assertEquals(0, result.get(0).chunkId());
            assertEquals(3, result.get(0).size());
            assertEquals(1, result.get(1).chunkId());
            assertEquals(3, result.get(1).size());
            assertEquals(2, result.get(2).chunkId());
            assertEquals(1, result.get(2).size());
        }

        @Test
        @DisplayName("returns one chunk per entry when maxChunkSize is 1")
        void maxChunkSizeIsOne_returnsOneChunkPerEntry() {
            Map<String, String> map = Map.of("k1", "v1", "k2", "v2", "k3", "v3");
            List<TranslationChunk> result = service.split(map, 1);
            assertEquals(3, result.size());
            for (int i = 0; i < 3; i++) {
                assertEquals(i, result.get(i).chunkId());
                assertEquals(1, result.get(i).size());
            }
        }

        @Test
        @DisplayName("returns single chunk for single entry map")
        void singleEntryMap_returnsSingleChunk() {
            Map<String, String> map = Map.of("k1", "v1");
            List<TranslationChunk> result = service.split(map, 5);
            assertEquals(1, result.size());
            assertEquals(0, result.getFirst().chunkId());
            assertEquals(1, result.getFirst().size());
        }

        @Test
        @DisplayName("returns an immutable list")
        void returnsImmutableList() {
            Map<String, String> map = Map.of("k1", "v1");
            List<TranslationChunk> result = service.split(map, 5);
            assertThrows(UnsupportedOperationException.class, () -> result.add(null));
        }
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("throws IllegalArgumentException when maxChunkSize is 0")
        void maxChunkSizeZero_throwsIllegalArgumentException() {
            Map<String, String> map = Map.of("k1", "v1");
            assertThrows(IllegalArgumentException.class, () -> service.split(map, 0));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when maxChunkSize is negative")
        void maxChunkSizeNegative_throwsIllegalArgumentException() {
            Map<String, String> map = Map.of("k1", "v1");
            assertThrows(IllegalArgumentException.class, () -> service.split(map, -1));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when maxChunkSize is Integer.MIN_VALUE")
        void maxChunkSizeMinValue_throwsIllegalArgumentException() {
            Map<String, String> map = Map.of("k1", "v1");
            assertThrows(IllegalArgumentException.class, () -> service.split(map, Integer.MIN_VALUE));
        }

        @Test
        @DisplayName("throws IllegalArgumentException with expected error message")
        void errorMessage_isCorrect() {
            Map<String, String> map = Map.of("k1", "v1");
            Exception ex = assertThrows(IllegalArgumentException.class, () -> service.split(map, 0));
            assertEquals("El tamaño máximo del lote debe ser estrictamente mayor a cero", ex.getMessage());
        }
    }
}
