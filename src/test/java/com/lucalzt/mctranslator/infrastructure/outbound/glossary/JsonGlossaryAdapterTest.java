package com.lucalzt.mctranslator.infrastructure.outbound.glossary;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lucalzt.mctranslator.domain.model.GlossaryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonGlossaryAdapter - integration tests with real ObjectMapper and filesystem")
class JsonGlossaryAdapterTest {

    @TempDir
    Path tempDir;

    private JsonMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }

    private JsonGlossaryAdapter createAdapter() {
        return new JsonGlossaryAdapter(objectMapper, tempDir);
    }

    private Path glossaryFilePath() {
        return tempDir.resolve(".mctranslator").resolve("glossary.json");
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
                    () -> new JsonGlossaryAdapter(null, tempDir));
        }

        @Test
        @DisplayName("throws NullPointerException when modpackPath is null")
        void throwsNullPointerException_whenModpackPathIsNull() {
            assertThrows(NullPointerException.class,
                    () -> new JsonGlossaryAdapter(objectMapper, null));
        }

        @Test
        @DisplayName("creates .mctranslator directory on first save when it does not exist")
        void createsDotMctranslatorDir_onFirstSave() {
            createAdapter().save("Stone", "Piedra");

            assertTrue(Files.exists(tempDir.resolve(".mctranslator")));
        }
    }

    // -------------------------------------------------------------------------
    // Save and lookup
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Save and lookup")
    class SaveAndLookup {

        @Test
        @DisplayName("saves entry and retrieves it via lookup")
        void savesAndRetrievesByLookup() {
            JsonGlossaryAdapter adapter = createAdapter();

            adapter.save("Stone", "Piedra");

            assertTrue(adapter.lookup("Stone").isPresent());
            assertEquals("Piedra", adapter.lookup("Stone").get());
        }

        @Test
        @DisplayName("lookup returns empty when term is not in glossary")
        void lookup_returnsEmpty_whenTermNotExists() {
            JsonGlossaryAdapter adapter = createAdapter();

            assertTrue(adapter.lookup("Nonexistent").isEmpty());
        }

        @Test
        @DisplayName("lookup is case-insensitive")
        void lookup_isCaseInsensitive() {
            JsonGlossaryAdapter adapter = createAdapter();
            adapter.save("Stone", "Piedra");

            assertEquals("Piedra", adapter.lookup("stone").get());
            assertEquals("Piedra", adapter.lookup("STONE").get());
            assertEquals("Piedra", adapter.lookup("StOnE").get());
        }

        @Test
        @DisplayName("save increments occurrences when term already exists")
        void save_incrementsOccurrences() {
            JsonGlossaryAdapter adapter = createAdapter();
            adapter.save("Stone", "Piedra");

            adapter.save("Stone", "Piedra");

            List<GlossaryEntry> all = adapter.findAll();
            assertEquals(1, all.size());
            assertEquals(2, all.getFirst().occurrences());
        }
    }

    // -------------------------------------------------------------------------
    // findRelevantTerms
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findRelevantTerms")
    class FindRelevantTerms {

        @Test
        @DisplayName("returns empty map when no candidates match")
        void returnsEmptyMap_whenNoCandidatesMatch() {
            JsonGlossaryAdapter adapter = createAdapter();
            adapter.save("Stone", "Piedra");

            Map<String, String> result = adapter.findRelevantTerms(Set.of("Iron", "Gold"));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns matching terms as map")
        void returnsMatchingTerms() {
            JsonGlossaryAdapter adapter = createAdapter();
            adapter.save("Stone", "Piedra");
            adapter.save("Iron", "Hierro");

            Map<String, String> result = adapter.findRelevantTerms(Set.of("Stone", "Gold", "Iron"));

            assertEquals(2, result.size());
            assertEquals("Piedra", result.get("Stone"));
            assertEquals("Hierro", result.get("Iron"));
        }

        @Test
        @DisplayName("returns empty map when candidates set is null")
        void returnsEmptyMap_whenCandidatesIsNull() {
            JsonGlossaryAdapter adapter = createAdapter();

            assertTrue(adapter.findRelevantTerms(null).isEmpty());
        }

        @Test
        @DisplayName("returns empty map when candidates set is empty")
        void returnsEmptyMap_whenCandidatesIsEmpty() {
            JsonGlossaryAdapter adapter = createAdapter();

            assertTrue(adapter.findRelevantTerms(Set.of()).isEmpty());
        }

        @Test
        @DisplayName("matches are case-insensitive")
        void matchingIsCaseInsensitive() {
            JsonGlossaryAdapter adapter = createAdapter();
            adapter.save("Stone", "Piedra");

            Map<String, String> result = adapter.findRelevantTerms(Set.of("stone", "STONE"));

            assertEquals(1, result.size());
        }
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("returns empty list when glossary is empty")
        void returnsEmptyList_whenGlossaryIsEmpty() {
            JsonGlossaryAdapter adapter = createAdapter();

            assertTrue(adapter.findAll().isEmpty());
        }

        @Test
        @DisplayName("returns all entries sorted alphabetically case-insensitive")
        void returnsAllEntriesSortedAlphabetically() {
            JsonGlossaryAdapter adapter = createAdapter();
            adapter.save("Iron", "Hierro");
            adapter.save("Diamond", "Diamante");
            adapter.save("Stone", "Piedra");

            List<GlossaryEntry> all = adapter.findAll();

            assertEquals(3, all.size());
            assertEquals("Diamond", all.get(0).termEn());
            assertEquals("Iron", all.get(1).termEn());
            assertEquals("Stone", all.get(2).termEn());
        }
    }

    // -------------------------------------------------------------------------
    // Persistence (JVM restart simulation)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Persistence")
    class Persistence {

        @Test
        @DisplayName("persists entries and recovers them on new adapter instance")
        void persistsAndRecoversAfterJvmRestart() {
            JsonGlossaryAdapter writer = createAdapter();
            writer.save("Stone", "Piedra");
            writer.save("Iron", "Hierro");
            writer = null;

            JsonGlossaryAdapter reader = createAdapter();

            assertTrue(reader.lookup("Stone").isPresent());
            assertEquals("Piedra", reader.lookup("Stone").get());
            assertEquals("Hierro", reader.lookup("Iron").get());
            assertEquals(2, reader.size());
        }

        @Test
        @DisplayName("recovers occurrences count after restart")
        void recoversOccurrencesAfterRestart() throws IOException {
            JsonGlossaryAdapter writer = createAdapter();
            writer.save("Stone", "Piedra");
            writer.save("Stone", "Piedra");
            writer.save("Stone", "Piedra");
            writer = null;

            JsonGlossaryAdapter reader = createAdapter();

            List<GlossaryEntry> all = reader.findAll();
            assertEquals(1, all.size());
            assertEquals(3, all.getFirst().occurrences());
        }

        @Test
        @DisplayName("starts empty when glossary file is corrupt")
        void startsEmpty_whenGlossaryFileIsCorrupt() throws IOException {
            Path glossaryFile = glossaryFilePath();
            Files.createDirectories(glossaryFile.getParent());
            Files.writeString(glossaryFile, "this is not valid json");

            JsonGlossaryAdapter adapter = createAdapter();

            assertEquals(0, adapter.size());
        }
    }

    // -------------------------------------------------------------------------
    // File structure
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("File structure")
    class FileStructure {

        @Test
        @DisplayName("writes glossary to expected path")
        void writesGlossaryToExpectedPath() {
            createAdapter().save("Stone", "Piedra");

            assertTrue(Files.exists(glossaryFilePath()));
            assertTrue(Files.isRegularFile(glossaryFilePath()));
        }

        @Test
        @DisplayName("writes valid JSON content with term and translation")
        void writesValidJsonContent() throws IOException {
            createAdapter().save("Stone", "Piedra");

            String json = Files.readString(glossaryFilePath());

            assertTrue(json.contains("Stone"));
            assertTrue(json.contains("Piedra"));
        }
    }

    // -------------------------------------------------------------------------
    // allEntries and size
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Utility methods")
    class UtilityMethods {

        @Test
        @DisplayName("allEntries returns all cache entries")
        void allEntries_returnsAllEntries() {
            JsonGlossaryAdapter adapter = createAdapter();
            adapter.save("Stone", "Piedra");
            adapter.save("Iron", "Hierro");

            Map<String, GlossaryEntry> entries = adapter.allEntries();

            assertEquals(2, entries.size());
        }

        @Test
        @DisplayName("size returns correct count")
        void size_returnsCorrectCount() {
            JsonGlossaryAdapter adapter = createAdapter();
            assertEquals(0, adapter.size());

            adapter.save("Stone", "Piedra");
            assertEquals(1, adapter.size());

            adapter.save("Iron", "Hierro");
            assertEquals(2, adapter.size());
        }
    }
}
