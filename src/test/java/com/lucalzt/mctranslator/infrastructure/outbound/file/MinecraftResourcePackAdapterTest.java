package com.lucalzt.mctranslator.infrastructure.outbound.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MinecraftResourcePackAdapter - integration tests with real ObjectMapper and filesystem")
class MinecraftResourcePackAdapterTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private MinecraftResourcePackAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new MinecraftResourcePackAdapter(objectMapper);
    }

    // -------------------------------------------------------------------------
    // Parameter validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Parameter validation")
    class ParameterValidation {

        @Test
        @DisplayName("throws NullPointerException when ObjectMapper is null")
        void throwsNullPointerException_whenObjectMapperIsNull() {
            assertThrows(NullPointerException.class,
                    () -> new MinecraftResourcePackAdapter(null));
        }

        @Test
        @DisplayName("throws NullPointerException when modId is null")
        void throwsNullPointerException_whenModIdIsNull() {
            TranslationResult result = new TranslationResult(0, Map.of(), Instant.now());
            assertThrows(NullPointerException.class,
                    () -> adapter.generate(null, result, tempDir));
        }

        @Test
        @DisplayName("throws NullPointerException when result is null")
        void throwsNullPointerException_whenResultIsNull() {
            assertThrows(NullPointerException.class,
                    () -> adapter.generate("testmod", null, tempDir));
        }

        @Test
        @DisplayName("throws NullPointerException when resourcePacksPath is null")
        void throwsNullPointerException_whenResourcePacksPathIsNull() {
            TranslationResult result = new TranslationResult(0, Map.of(), Instant.now());
            assertThrows(NullPointerException.class,
                    () -> adapter.generate("testmod", result, null));
        }
    }

    // -------------------------------------------------------------------------
    // Generation scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Generation")
    class Generation {

        @Test
        @DisplayName("creates pack.mcmeta and assets/<modId>/lang/es_es.json with translations")
        void createsResourcePackStructure_withTranslations() throws IOException {
            Map<String, String> translations = Map.of(
                    "item.test.sword", "Espada de Prueba",
                    "block.test.ore", "Mena de Prueba"
            );
            TranslationResult result = new TranslationResult(1, translations, Instant.now());

            adapter.generate("testmod", result, tempDir);

            Path packMcmeta = tempDir.resolve("pack.mcmeta");
            Path esEsFile = tempDir.resolve("assets/testmod/lang/es_es.json");
            assertTrue(Files.exists(packMcmeta));
            assertTrue(Files.exists(esEsFile));

            JsonNode esJson = objectMapper.readTree(esEsFile.toFile());
            assertEquals("Espada de Prueba", esJson.get("item.test.sword").asText());
            assertEquals("Mena de Prueba", esJson.get("block.test.ore").asText());
        }

        @Test
        @DisplayName("does not overwrite existing pack.mcmeta")
        void doesNotOverwriteExistingPackMcmeta() throws IOException {
            Path packMcmeta = tempDir.resolve("pack.mcmeta");
            Map<String, Object> originalMeta = Map.of(
                    "pack", Map.of("pack_format", 99, "description", "custom description")
            );
            objectMapper.writeValue(packMcmeta.toFile(), originalMeta);

            TranslationResult result = new TranslationResult(0, Map.of("key", "val"), Instant.now());
            adapter.generate("testmod", result, tempDir);

            JsonNode loaded = objectMapper.readTree(packMcmeta.toFile());
            assertEquals(99, loaded.get("pack").get("pack_format").asInt());
            assertEquals("custom description", loaded.get("pack").get("description").asText());
        }

        @Test
        @DisplayName("writes empty JSON object when translations are empty")
        void writesEmptyJson_whenTranslationsAreEmpty() throws IOException {
            TranslationResult result = new TranslationResult(0, Map.of(), Instant.now());

            adapter.generate("emptymod", result, tempDir);

            Path esEsFile = tempDir.resolve("assets/emptymod/lang/es_es.json");
            JsonNode root = objectMapper.readTree(esEsFile.toFile());
            assertTrue(root.isEmpty());
        }

        @Test
        @DisplayName("handles special characters like ñ, é, quotes and ampersands")
        void handlesSpecialCharacters() throws IOException {
            Map<String, String> translations = Map.of(
                    "item.test.sword", "Espada ñoña con \"comillas\"",
                    "item.test.pickaxe", "Pico de ébano & más"
            );
            TranslationResult result = new TranslationResult(0, translations, Instant.now());

            adapter.generate("specialmod", result, tempDir);

            Path esEsFile = tempDir.resolve("assets/specialmod/lang/es_es.json");
            JsonNode root = objectMapper.readTree(esEsFile.toFile());
            assertEquals("Espada ñoña con \"comillas\"", root.get("item.test.sword").asText());
            assertEquals("Pico de ébano & más", root.get("item.test.pickaxe").asText());
        }

        @Test
        @DisplayName("creates intermediate directories when resourcePacksPath does not exist")
        void createsIntermediateDirectories_whenPathDoesNotExist() throws IOException {
            Path deepPath = tempDir.resolve("nonexistent/subdir/pack");
            TranslationResult result = new TranslationResult(0, Map.of("key", "val"), Instant.now());

            adapter.generate("deepmod", result, deepPath);

            assertTrue(Files.exists(deepPath.resolve("assets/deepmod/lang/es_es.json")));
        }
    }

    // -------------------------------------------------------------------------
    // hasCompleteTranslation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("hasCompleteTranslation")
    class HasCompleteTranslation {

        @Test
        @DisplayName("returns false when es_es.json does not exist")
        void returnsFalse_whenFileDoesNotExist() {
            assertFalse(adapter.hasCompleteTranslation("testmod", Set.of("key.1"), tempDir));
        }

        @Test
        @DisplayName("returns false when originalKeys is empty")
        void returnsFalse_whenOriginalKeysIsEmpty() throws IOException {
            Path esEsFile = tempDir.resolve("assets/testmod/lang/es_es.json");
            Files.createDirectories(esEsFile.getParent());
            objectMapper.writeValue(esEsFile.toFile(), Map.of("key.1", "valor"));

            assertFalse(adapter.hasCompleteTranslation("testmod", Set.of(), tempDir));
        }

        @Test
        @DisplayName("returns true when es_es.json contains all original keys")
        void returnsTrue_whenAllKeysPresent() throws IOException {
            Map<String, String> translations = Map.of(
                    "key.one", "Uno",
                    "key.two", "Dos"
            );
            Path esEsFile = tempDir.resolve("assets/testmod/lang/es_es.json");
            Files.createDirectories(esEsFile.getParent());
            objectMapper.writeValue(esEsFile.toFile(), translations);

            assertTrue(adapter.hasCompleteTranslation("testmod", Set.of("key.one", "key.two"), tempDir));
        }

        @Test
        @DisplayName("returns true when es_es.json has extra keys beyond original")
        void returnsTrue_whenFileHasExtraKeys() throws IOException {
            Map<String, String> translations = Map.of(
                    "key.one", "Uno",
                    "key.two", "Dos",
                    "key.three", "Tres"
            );
            Path esEsFile = tempDir.resolve("assets/testmod/lang/es_es.json");
            Files.createDirectories(esEsFile.getParent());
            objectMapper.writeValue(esEsFile.toFile(), translations);

            assertTrue(adapter.hasCompleteTranslation("testmod", Set.of("key.one", "key.two"), tempDir));
        }

        @Test
        @DisplayName("returns false when es_es.json is missing some original keys")
        void returnsFalse_whenFileIsMissingKeys() throws IOException {
            Map<String, String> translations = Map.of(
                    "key.one", "Uno"
            );
            Path esEsFile = tempDir.resolve("assets/testmod/lang/es_es.json");
            Files.createDirectories(esEsFile.getParent());
            objectMapper.writeValue(esEsFile.toFile(), translations);

            assertFalse(adapter.hasCompleteTranslation("testmod", Set.of("key.one", "key.two"), tempDir));
        }

        @Test
        @DisplayName("returns false when es_es.json contains corrupt JSON")
        void returnsFalse_whenFileIsCorrupt() throws IOException {
            Path esEsFile = tempDir.resolve("assets/testmod/lang/es_es.json");
            Files.createDirectories(esEsFile.getParent());
            Files.writeString(esEsFile, "not valid json");

            assertFalse(adapter.hasCompleteTranslation("testmod", Set.of("key.one"), tempDir));
        }

        @Test
        @DisplayName("throws NullPointerException when modId is null")
        void throwsNullPointerException_whenModIdIsNull() {
            assertThrows(NullPointerException.class,
                    () -> adapter.hasCompleteTranslation(null, Set.of("key"), tempDir));
        }

        @Test
        @DisplayName("throws NullPointerException when originalKeys is null")
        void throwsNullPointerException_whenOriginalKeysIsNull() {
            assertThrows(NullPointerException.class,
                    () -> adapter.hasCompleteTranslation("testmod", null, tempDir));
        }

        @Test
        @DisplayName("throws NullPointerException when resourcePacksPath is null")
        void throwsNullPointerException_whenResourcePacksPathIsNull() {
            assertThrows(NullPointerException.class,
                    () -> adapter.hasCompleteTranslation("testmod", Set.of("key"), null));
        }
    }
}
