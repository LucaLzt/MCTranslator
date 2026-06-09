package com.lucalzt.mctranslator.infrastructure.outbound.quest;

import com.lucalzt.mctranslator.domain.model.QuestData;
import com.lucalzt.mctranslator.domain.model.QuestSystemType;
import com.lucalzt.mctranslator.domain.service.SnbtSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FtbQuestsModernAdapter - SNBT parsing and I/O")
class FtbQuestsModernAdapterTest {

    private final SnbtSanitizer sanitizer = new SnbtSanitizer();
    private FtbQuestsModernAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FtbQuestsModernAdapter(sanitizer);
    }

    @Nested
    @DisplayName("parseSnbt")
    class ParseSnbt {

        @Test
        @DisplayName("parses simple string values")
        void parsesSimpleStringValues() {
            String input = """
                    {
                      "quest.1.title": "Starting Out"
                      "quest.1.desc": "Begin your journey"
                    }""";
            Map<String, String> result = FtbQuestsModernAdapter.parseSnbt(input);
            assertEquals(2, result.size());
            assertEquals("Starting Out", result.get("quest.1.title"));
            assertEquals("Begin your journey", result.get("quest.1.desc"));
        }

        @Test
        @DisplayName("parses unquoted dotted keys")
        void parsesUnquotedDottedKeys() {
            String input = "{chapter.1.title: \"Chapter One\"}";
            Map<String, String> result = FtbQuestsModernAdapter.parseSnbt(input);
            assertEquals("Chapter One", result.get("chapter.1.title"));
        }

        @Test
        @DisplayName("parses array values")
        void parsesArrayValues() {
            String input = """
                    {
                      "quest.1.desc": ["Welcome", "Start here"]
                      "quest.1.title": "Quest Title"
                    }""";
            Map<String, String> result = FtbQuestsModernAdapter.parseSnbt(input);
            assertEquals(2, result.size());
            assertEquals("[\"Welcome\", \"Start here\"]", result.get("quest.1.desc"));
            assertEquals("Quest Title", result.get("quest.1.title"));
        }

        @Test
        @DisplayName("handles optional commas between entries")
        void handlesOptionalCommas() {
            String input = "{\"a\": \"1\", \"b\": \"2\", \"c\": \"3\"}";
            Map<String, String> result = FtbQuestsModernAdapter.parseSnbt(input);
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("handles mixed quoted and unquoted keys")
        void handlesMixedKeyStyles() {
            String input = "{\"quoted.key\": \"val1\", plain: \"val2\"}";
            Map<String, String> result = FtbQuestsModernAdapter.parseSnbt(input);
            assertEquals("val1", result.get("quoted.key"));
            assertEquals("val2", result.get("plain"));
        }

        @Test
        @DisplayName("preserves insertion order")
        void preservesInsertionOrder() {
            String input = """
                    {
                      "z.last": "final"
                      "a.first": "initial"
                      "m.mid": "middle"
                    }""";
            Map<String, String> result = FtbQuestsModernAdapter.parseSnbt(input);
            var keys = result.keySet().iterator();
            assertEquals("z.last", keys.next());
            assertEquals("a.first", keys.next());
            assertEquals("m.mid", keys.next());
        }

        @Test
        @DisplayName("returns empty map for empty object")
        void returnsEmptyMap_forEmptyObject() {
            Map<String, String> result = FtbQuestsModernAdapter.parseSnbt("{}");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("handles multiline array values")
        void handlesMultilineArrayValues() {
            String input = """
                    {
                      "quest.1.desc": [
                        "Line one",
                        "Line two"
                      ]
                    }""";
            Map<String, String> result = FtbQuestsModernAdapter.parseSnbt(input);
            assertTrue(result.containsKey("quest.1.desc"));
            assertTrue(result.get("quest.1.desc").contains("Line one"));
        }
    }

    @Nested
    @DisplayName("generateSnbt")
    class GenerateSnbt {

        @Test
        @DisplayName("generates valid SNBT from entries")
        void generatesValidSnbt() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("quest.1.title", "Quest Title");
            String result = FtbQuestsModernAdapter.generateSnbt(entries);
            assertTrue(result.startsWith("{"));
            assertTrue(result.endsWith("}"));
            assertTrue(result.contains("\"quest.1.title\""));
            assertTrue(result.contains("\"Quest Title\""));
        }

        @Test
        @DisplayName("wraps string values in quotes")
        void wrapsStringValuesInQuotes() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("key", "value");
            String result = FtbQuestsModernAdapter.generateSnbt(entries);
            assertTrue(result.contains("\"value\""));
        }

        @Test
        @DisplayName("preserves array values without extra quoting")
        void preservesArrayValues() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("quest.1.desc", "[\"a\", \"b\"]");
            String result = FtbQuestsModernAdapter.generateSnbt(entries);
            assertTrue(result.contains("[\"a\", \"b\"]"));
            assertFalse(result.contains("\"[\""));
        }

        @Test
        @DisplayName("escapes quotes inside string values")
        void escapesQuotesInsideStringValues() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("key", "value with \"quotes\"");
            String result = FtbQuestsModernAdapter.generateSnbt(entries);
            assertTrue(result.contains("\\\"quotes\\\""));
        }

        @Test
        @DisplayName("quotes keys with dots")
        void quotesKeysWithDots() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("chapter.1.title", "Título");
            entries.put("plain", "value");
            String result = FtbQuestsModernAdapter.generateSnbt(entries);
            assertTrue(result.contains("\"chapter.1.title\""));
            assertTrue(result.contains("plain: \"value\""));
        }

        @Test
        @DisplayName("preserves key order")
        void preservesKeyOrder() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("z.last", "final");
            entries.put("a.first", "initial");
            String result = FtbQuestsModernAdapter.generateSnbt(entries);
            int idxZ = result.indexOf("z.last");
            int idxA = result.indexOf("a.first");
            assertTrue(idxZ < idxA, "z.last should come before a.first");
        }
    }

    @Nested
    @DisplayName("extract")
    class Extract {

        @Test
        @DisplayName("reads and parses en_us.snbt")
        void readsAndParsesEnUsSnbt(@TempDir Path tempDir) throws IOException {
            Path langDir = tempDir.resolve("config/ftbquests/quests/lang");
            Files.createDirectories(langDir);
            Files.writeString(langDir.resolve("en_us.snbt"), """
                    {
                      "quest.1.title": "Starting Out"
                      "quest.1.desc": ["Welcome!", "Start here."]
                    }""");

            QuestData result = adapter.extract(tempDir);
            assertEquals(QuestSystemType.FTB_QUESTS_MODERN, result.systemType());
            assertEquals(2, result.entries().size());
            assertEquals("Starting Out", result.entries().get("quest.1.title"));
        }

        @Test
        @DisplayName("returns NONE when file does not exist")
        void returnsNone_whenFileNotFound(@TempDir Path tempDir) {
            QuestData result = adapter.extract(tempDir);
            assertEquals(QuestSystemType.NONE, result.systemType());
            assertTrue(result.entries().isEmpty());
        }

        @Test
        @DisplayName("applies SnbtSanitizer before parsing")
        void appliesSanitizerBeforeParsing(@TempDir Path tempDir) throws IOException {
            Path langDir = tempDir.resolve("config/ftbquests/quests/lang");
            Files.createDirectories(langDir);
            Files.writeString(langDir.resolve("en_us.snbt"), "```snbt\n{\"key\": \"value\"}\n```");

            QuestData result = adapter.extract(tempDir);
            assertEquals("value", result.entries().get("key"));
        }

        @Test
        @DisplayName("returns NONE on corrupt file")
        void returnsNone_onCorruptFile(@TempDir Path tempDir) throws IOException {
            Path langDir = tempDir.resolve("config/ftbquests/quests/lang");
            Files.createDirectories(langDir);
            Files.writeString(langDir.resolve("en_us.snbt"), "this is not snbt at all");

            QuestData result = adapter.extract(tempDir);
            assertEquals(QuestSystemType.NONE, result.systemType());
        }
    }

    @Nested
    @DisplayName("write")
    class Write {

        @Test
        @DisplayName("writes es_es.snbt with translations")
        void writesEsEsSnbt(@TempDir Path tempDir) {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("quest.1.title", "Starting Out");
            entries.put("quest.1.desc", "[\"Begin here\"]");
            QuestData original = new QuestData(QuestSystemType.FTB_QUESTS_MODERN, entries, new byte[0]);
            Map<String, String> translations = Map.of(
                    "quest.1.title", "Comenzando",
                    "quest.1.desc", "[\"Empieza aquí\"]"
            );

            adapter.write(tempDir, original, translations);

            Path output = tempDir.resolve("config/ftbquests/quests/lang/es_es.snbt");
            assertTrue(Files.exists(output));
        }

        @Test
        @DisplayName("throws on non-FTB_MODERN system type")
        void throwsOnWrongSystemType() {
            QuestData original = new QuestData(QuestSystemType.NONE, Map.of(), new byte[0]);
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.write(Path.of("."), original, Map.of()));
        }

        @Test
        @DisplayName("produces parseable SNBT")
        void producesParseableSnbt(@TempDir Path tempDir) throws IOException {
            Map<String, String> translations = Map.of(
                    "quest.1.title", "Comenzando",
                    "quest.2.desc", "[\"Línea de desc\"]"
            );
            QuestData original = new QuestData(QuestSystemType.FTB_QUESTS_MODERN, translations, new byte[0]);

            adapter.write(tempDir, original, translations);

            Path output = tempDir.resolve("config/ftbquests/quests/lang/es_es.snbt");
            String written = Files.readString(output);
            Map<String, String> parsed = FtbQuestsModernAdapter.parseSnbt(written);
            assertEquals("Comenzando", parsed.get("quest.1.title"));
            assertEquals("[\"Línea de desc\"]", parsed.get("quest.2.desc"));
        }
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("throws NullPointerException when sanitizer is null")
        void throwsNullPointerException_whenSanitizerIsNull() {
            assertThrows(NullPointerException.class,
                    () -> new FtbQuestsModernAdapter(null));
        }
    }
}
