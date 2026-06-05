package com.lucalzt.mctranslator.infrastructure.outbound.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OllamaRestClientAdapter - real integration test against local Ollama server")
class OllamaRestClientAdapterTest {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";

    private ObjectMapper objectMapper;
    private RestClient.Builder restClientBuilder;
    private OllamaRestClientAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        restClientBuilder = RestClient.builder();
        adapter = new OllamaRestClientAdapter(restClientBuilder, objectMapper);
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
                    () -> new OllamaRestClientAdapter(restClientBuilder, null));
        }

        @Test
        @DisplayName("throws NullPointerException when chunk is null")
        void throwsNullPointerException_whenChunkIsNull() {
            assertThrows(NullPointerException.class,
                    () -> adapter.translate(null));
        }
    }

    // -------------------------------------------------------------------------
    // Translation (requires local Ollama)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Translation (requires local Ollama)")
    class Translation {

        @BeforeEach
        void ensureOllamaIsAvailable() {
            Assumptions.assumeTrue(checkOllamaConnectivity(),
                    "Ollama is not running at " + OLLAMA_BASE_URL + ". Skipping integration test.");
        }

        @Test
        @DisplayName("translates multiple keys preserving structure and format codes")
        void translatesMultipleKeys_preservingStructureAndFormatCodes() {
            Map<String, String> originalKeys = Map.of(
                    "item.minecraft.iron_sword", "Iron Sword",
                    "block.minecraft.oak_planks", "Oak Planks",
                    "item.mctranslator.ruby_ingot", "Ruby Ingot",
                    "tooltip.mctranslator.warning", "Do not §cdelete §rthis file!",
                    "chat.mctranslator.welcome", "Welcome %s! You have %d items."
            );
            TranslationChunk chunk = new TranslationChunk(42, originalKeys);

            TranslationResult result = adapter.translate(chunk);

            assertNotNull(result);
            assertEquals(42, result.chunkId());
            assertEquals(originalKeys.size(), result.translatedTranslations().size());
            assertTrue(result.translatedTranslations().containsKey("tooltip.mctranslator.warning"));

            String translatedWarning = result.translatedTranslations().get("tooltip.mctranslator.warning");
            assertTrue(translatedWarning.contains("§c") && translatedWarning.contains("§r"),
                    "Format codes §c and §r must be preserved in the translation");
        }

        @Test
        @DisplayName("translates chunk preserving chunkId for different chunks")
        void translatesChunk_preservingChunkId() {
            TranslationChunk chunk = new TranslationChunk(99, Map.of(
                    "key.test.one", "First Value",
                    "key.test.two", "Second Value"
            ));

            TranslationResult result = adapter.translate(chunk);

            assertNotNull(result);
            assertEquals(99, result.chunkId());
            assertEquals(2, result.translatedTranslations().size());
        }

        @Test
        @DisplayName("preserves mod names during translation")
        void preservesModNamesDuringTranslation() {
            Map<String, String> keys = Map.of(
                    "item.bosses_of_mass_destruction.title", "Bosses of Mass Destruction",
                    "item.simply_swords.guide", "Simply Swords Guide",
                    "item.archon.artifact", "Archon Artifact"
            );
            TranslationChunk chunk = new TranslationChunk(7, keys);

            TranslationResult result = adapter.translate(chunk);

            assertNotNull(result);
            assertEquals(keys.size(), result.translatedTranslations().size());
            assertTrue(result.translatedTranslations().containsKey("item.bosses_of_mass_destruction.title"));
            assertTrue(result.translatedTranslations().containsKey("item.simply_swords.guide"));
            assertTrue(result.translatedTranslations().containsKey("item.archon.artifact"));
        }
    }

    private boolean checkOllamaConnectivity() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_BASE_URL + "/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
