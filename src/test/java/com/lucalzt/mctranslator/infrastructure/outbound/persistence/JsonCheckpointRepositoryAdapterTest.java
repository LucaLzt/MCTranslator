package com.lucalzt.mctranslator.infrastructure.outbound.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonCheckpointRepositoryAdapter - integration tests with real ObjectMapper and filesystem")
class JsonCheckpointRepositoryAdapterTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    private Path checkpointPath(String modId) {
        return tempDir.resolve("resourcepacks/MCTranslator-ES/.checkpoints/" + modId + ".json");
    }

    private JsonCheckpointRepositoryAdapter createAdapter() {
        JsonCheckpointRepositoryAdapter adapter = new JsonCheckpointRepositoryAdapter(objectMapper);
        adapter.setModpackPath(tempDir);
        return adapter;
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("throws NullPointerException when ObjectMapper is null")
        void throwsNullPointerException_whenObjectMapperIsNull() {
            assertThrows(NullPointerException.class,
                    () -> new JsonCheckpointRepositoryAdapter(null));
        }

        @Test
        @DisplayName("throws NullPointerException when modpackPath is null")
        void throwsNullPointerException_whenModpackPathIsNull() {
            JsonCheckpointRepositoryAdapter adapter = new JsonCheckpointRepositoryAdapter(objectMapper);
            assertThrows(NullPointerException.class,
                    () -> adapter.setModpackPath(null));
        }
    }

    // -------------------------------------------------------------------------
    // Configuration validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Configuration validation")
    class ConfigurationValidation {

        @Test
        @DisplayName("throws IllegalStateException when saving without setting modpack path")
        void throwsIllegalStateException_whenSavingWithoutModpackPath() {
            JsonCheckpointRepositoryAdapter adapter = new JsonCheckpointRepositoryAdapter(objectMapper);
            assertThrows(IllegalStateException.class,
                    () -> adapter.save("anyMod", Set.of("key.1")));
        }

        @Test
        @DisplayName("throws IllegalStateException when loading without setting modpack path")
        void throwsIllegalStateException_whenLoadingWithoutModpackPath() {
            JsonCheckpointRepositoryAdapter adapter = new JsonCheckpointRepositoryAdapter(objectMapper);
            assertThrows(IllegalStateException.class,
                    () -> adapter.load("anyMod"));
        }
    }

    // -------------------------------------------------------------------------
    // Save and load
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Save and load")
    class SaveAndLoad {

        @Test
        @DisplayName("saves and loads keys successfully")
        void savesAndLoadsKeysSuccessfully() {
            JsonCheckpointRepositoryAdapter adapter = createAdapter();
            Set<String> keys = Set.of("key.item.ruby_sword", "key.block.deepslate_ore", "key.advancement.root.title");

            adapter.save("testmod", keys);
            Set<String> loaded = adapter.load("testmod");

            assertEquals(keys, loaded);
        }

        @Test
        @DisplayName("simulates JVM restart and recovers checkpoint from disk")
        void simulatesJvmRestart_andRecoversCheckpoint() {
            Set<String> keys = Set.of("key.alpha", "key.beta");

            JsonCheckpointRepositoryAdapter writer = createAdapter();
            writer.save("persistentmod", keys);
            writer = null;

            JsonCheckpointRepositoryAdapter reader = createAdapter();
            Set<String> loaded = reader.load("persistentmod");

            assertEquals(keys, loaded);
        }

        @Test
        @DisplayName("saves and loads empty key set")
        void savesAndLoadsEmptyKeySet() {
            JsonCheckpointRepositoryAdapter adapter = createAdapter();

            adapter.save("emptymod", Set.of());
            Set<String> loaded = adapter.load("emptymod");

            assertTrue(loaded.isEmpty());
        }

        @Test
        @DisplayName("throws NullPointerException when saving with null modId")
        void throwsNullPointerException_whenSavingWithNullModId() {
            JsonCheckpointRepositoryAdapter adapter = createAdapter();
            assertThrows(NullPointerException.class,
                    () -> adapter.save(null, Set.of("key.1")));
        }

        @Test
        @DisplayName("throws NullPointerException when saving with null keys")
        void throwsNullPointerException_whenSavingWithNullKeys() {
            JsonCheckpointRepositoryAdapter adapter = createAdapter();
            assertThrows(NullPointerException.class,
                    () -> adapter.save("testmod", null));
        }

        @Test
        @DisplayName("throws NullPointerException when loading with null modId")
        void throwsNullPointerException_whenLoadingWithNullModId() {
            JsonCheckpointRepositoryAdapter adapter = createAdapter();
            assertThrows(NullPointerException.class,
                    () -> adapter.load(null));
        }

        @Test
        @DisplayName("saves multiple mods independently")
        void savesMultipleModsIndependently() throws IOException {
            JsonCheckpointRepositoryAdapter adapter = createAdapter();
            Set<String> keysModA = Set.of("key.a1", "key.a2");
            Set<String> keysModB = Set.of("key.b1");

            adapter.save("modA", keysModA);
            adapter.save("modB", keysModB);

            assertTrue(Files.exists(checkpointPath("modA")));
            assertTrue(Files.exists(checkpointPath("modB")));
            assertEquals(keysModA, adapter.load("modA"));
            assertEquals(keysModB, adapter.load("modB"));
        }
    }

    // -------------------------------------------------------------------------
    // Load scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Load scenarios")
    class LoadScenarios {

        @Test
        @DisplayName("returns empty set when checkpoint file does not exist")
        void returnsEmptySet_whenCheckpointFileDoesNotExist() {
            JsonCheckpointRepositoryAdapter adapter = createAdapter();

            Set<String> loaded = adapter.load("nonexistent");

            assertNotNull(loaded);
            assertTrue(loaded.isEmpty());
        }

        @Test
        @DisplayName("returns empty set when checkpoint file is corrupt")
        void returnsEmptySet_whenCheckpointFileIsCorrupt() throws IOException {
            Path corruptFile = checkpointPath("corruptmod");
            Files.createDirectories(corruptFile.getParent());
            Files.writeString(corruptFile, "this is not valid json");

            JsonCheckpointRepositoryAdapter adapter = createAdapter();
            Set<String> loaded = adapter.load("corruptmod");

            assertNotNull(loaded);
            assertTrue(loaded.isEmpty());
        }

        @Test
        @DisplayName("returns empty set when JSON has null translatedKeys")
        void returnsEmptySet_whenJsonHasNullTranslatedKeys() throws IOException {
            Path nullKeysFile = checkpointPath("nullkeysmod");
            Files.createDirectories(nullKeysFile.getParent());
            Files.writeString(nullKeysFile, "{\"modId\":\"nullkeysmod\",\"translatedKeys\":null}");

            JsonCheckpointRepositoryAdapter adapter = createAdapter();
            Set<String> loaded = adapter.load("nullkeysmod");

            assertNotNull(loaded);
            assertTrue(loaded.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // File structure
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("File structure")
    class FileStructure {

        @Test
        @DisplayName("writes checkpoint to expected path")
        void writesCheckpointToExpectedPath() {
            JsonCheckpointRepositoryAdapter adapter = createAdapter();
            String modId = "pathcheck";
            Set<String> keys = Set.of("key.test");

            adapter.save(modId, keys);

            Path expected = checkpointPath(modId);
            assertTrue(Files.exists(expected));
            assertTrue(Files.isRegularFile(expected));
        }

        @Test
        @DisplayName("writes valid JSON content containing modId and keys")
        void writesValidJsonContent_containingModIdAndKeys() throws IOException {
            JsonCheckpointRepositoryAdapter adapter = createAdapter();
            String modId = "contentcheck";
            Set<String> keys = Set.of("key.sierra", "key.uniform");

            adapter.save(modId, keys);

            String json = Files.readString(checkpointPath(modId));
            assertTrue(json.contains(modId));
            assertTrue(json.contains("key.sierra"));
            assertTrue(json.contains("key.uniform"));
        }
    }
}
