package com.lucalzt.mctranslator.domain.service;

import com.lucalzt.mctranslator.domain.model.ModLanguageFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CheckpointFilter - filtering already translated keys")
class CheckpointFilterTest {

    private final CheckpointFilter filter = new CheckpointFilter();

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("throws NullPointerException when sourceFile is null")
        void nullSourceFile_throwsNullPointerException() {
            Set<String> keys = Set.of("key1");
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> filter.filter(null, keys));
            assertEquals("El archivo de idioma original no puede ser nulo", ex.getMessage());
        }

        @Test
        @DisplayName("throws NullPointerException when translatedKeys is null")
        void nullTranslatedKeys_throwsNullPointerException() {
            ModLanguageFile file = new ModLanguageFile("mod", "en_us", Map.of("a", "1"));
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> filter.filter(file, null));
            assertEquals("El conjunto de claves procesadas no puede ser nulo", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("returns all entries when no keys are translated")
        void noTranslatedKeys_returnsAllEntries() {
            ModLanguageFile file = new ModLanguageFile("mod", "en_us",
                    Map.of("key.a", "value.a", "key.b", "value.b"));
            Map<String, String> result = filter.filter(file, Set.of());
            assertEquals(2, result.size());
            assertEquals("value.a", result.get("key.a"));
            assertEquals("value.b", result.get("key.b"));
        }

        @Test
        @DisplayName("returns empty map when all keys are translated")
        void allTranslated_returnsEmptyMap() {
            ModLanguageFile file = new ModLanguageFile("mod", "en_us",
                    Map.of("k1", "v1", "k2", "v2"));
            Map<String, String> result = filter.filter(file, Set.of("k1", "k2"));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns only pending keys when some are translated")
        void mixedTranslated_returnsOnlyPending() {
            ModLanguageFile file = new ModLanguageFile("mod", "en_us",
                    Map.of("k1", "v1", "k2", "v2", "k3", "v3"));
            Map<String, String> result = filter.filter(file, Set.of("k2"));
            assertEquals(2, result.size());
            assertTrue(result.containsKey("k1"));
            assertTrue(result.containsKey("k3"));
            assertFalse(result.containsKey("k2"));
        }

        @Test
        @DisplayName("returns empty map when source has no translations")
        void emptyTranslations_returnsEmptyMap() {
            ModLanguageFile file = new ModLanguageFile("mod", "en_us", Map.of());
            Map<String, String> result = filter.filter(file, Set.of("k1"));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("ignores extra keys not present in source")
        void extraKeysInTranslated_ignoresThem() {
            ModLanguageFile file = new ModLanguageFile("mod", "en_us",
                    Map.of("k1", "v1"));
            Map<String, String> result = filter.filter(file, Set.of("k1", "inexistente", "otra"));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("preserves iteration order of source file")
        void preservesIterationOrderOfSourceFile() {
            Map<String, String> original = new LinkedHashMap<>();
            original.put("z", "última");
            original.put("a", "primera");
            original.put("m", "media");
            ModLanguageFile file = new ModLanguageFile("mod", "en_us", original);
            Map<String, String> result = filter.filter(file, Set.of());
            assertEquals(new ArrayList<>(file.translations().keySet()), new ArrayList<>(result.keySet()));
        }

        @Test
        @DisplayName("returns an unmodifiable map")
        void returnedMap_isUnmodifiable() {
            ModLanguageFile file = new ModLanguageFile("mod", "en_us",
                    Map.of("k1", "v1"));
            Map<String, String> result = filter.filter(file, Set.of());
            assertThrows(UnsupportedOperationException.class, () -> result.put("k2", "v2"));
        }
    }
}
