package com.lucalzt.mctranslator.infrastructure.outbound.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.model.ModLanguageFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ZipFileSystemExtractorAdapter - integration tests with real ObjectMapper and jar filesystem")
class ZipFileSystemExtractorAdapterTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private ZipFileSystemExtractorAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new ZipFileSystemExtractorAdapter(objectMapper);
    }

    private Path createMockJar(Path targetPath, Map<String, String> entries) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(targetPath))) {
            for (var entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return targetPath;
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
                    () -> new ZipFileSystemExtractorAdapter(null));
        }

        @Test
        @DisplayName("throws NullPointerException when jarPath is null")
        void throwsNullPointerException_whenJarPathIsNull() {
            assertThrows(NullPointerException.class,
                    () -> adapter.extract(null));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when jarPath does not exist")
        void throwsIllegalArgumentException_whenJarPathDoesNotExist() {
            Path ghostPath = tempDir.resolve("nonexistent.jar");
            assertThrows(IllegalArgumentException.class,
                    () -> adapter.extract(ghostPath));
        }
    }

    // -------------------------------------------------------------------------
    // Extraction scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Extraction")
    class Extraction {

        @Test
        @DisplayName("extracts ModLanguageFile from valid jar with one mod")
        void extractsModLanguageFile_fromValidJar() throws IOException {
            String modId = "cool_sword";
            Map<String, String> translations = Map.of(
                    "item.cool_sword.blade", "Cool Blade",
                    "item.cool_sword.hilt", "Cool Hilt"
            );
            String jsonContent = objectMapper.writeValueAsString(translations);
            Path jarPath = createMockJar(tempDir.resolve("cool_sword-1.0.jar"), Map.of(
                    "assets/" + modId + "/lang/en_us.json", jsonContent
            ));

            ModLanguageFile result = adapter.extract(jarPath);

            assertEquals(modId, result.modId());
            assertEquals("en_us", result.sourceLanguage());
            assertEquals(translations, result.translations());
        }

        @Test
        @DisplayName("extracts empty translations when en_us.json contains empty object")
        void extractsEmptyTranslations_whenJsonIsEmptyObject() throws IOException {
            Path jarPath = createMockJar(tempDir.resolve("emptymod.jar"), Map.of(
                    "assets/emptymod/lang/en_us.json", "{}"
            ));

            ModLanguageFile result = adapter.extract(jarPath);

            assertEquals("emptymod", result.modId());
            assertTrue(result.translations().isEmpty());
        }

        @Test
        @DisplayName("returns fallback when jar has no assets directory")
        void returnsFallback_whenNoAssetsDirectory() throws IOException {
            Path jarPath = createMockJar(tempDir.resolve("NoAssetsMod.jar"), Map.of(
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0"
            ));

            ModLanguageFile result = adapter.extract(jarPath);

            assertEquals("noassetsmod", result.modId());
            assertEquals("en_us", result.sourceLanguage());
            assertTrue(result.translations().isEmpty());
        }

        @Test
        @DisplayName("returns fallback when assets directory has no lang file")
        void returnsFallback_whenNoLangFileFound() throws IOException {
            Path jarPath = createMockJar(tempDir.resolve("nolangmod.jar"), Map.of(
                    "assets/nolangmod/pack.png", "pretend-bytes"
            ));

            ModLanguageFile result = adapter.extract(jarPath);

            assertEquals("nolangmod", result.modId());
            assertTrue(result.translations().isEmpty());
        }

        @Test
        @DisplayName("finds one mod when multiple mod assets are present")
        void findsOneMod_whenMultipleAssetsPresent() throws IOException {
            String jsonA = objectMapper.writeValueAsString(Map.of("key.a", "valueA"));
            String jsonB = objectMapper.writeValueAsString(Map.of("key.b", "valueB"));
            Path jarPath = createMockJar(tempDir.resolve("multimod.jar"), Map.of(
                    "assets/modA/lang/en_us.json", jsonA,
                    "assets/modB/lang/en_us.json", jsonB
            ));

            ModLanguageFile result = adapter.extract(jarPath);

            assertTrue(result.modId().equals("modA") || result.modId().equals("modB"));
            assertEquals(1, result.translations().size());
        }

        @Test
        @DisplayName("uses fallback modId derived from filename when no assets found")
        void usesFallbackModId_fromFilename_whenNoAssets() throws IOException {
            Path jarPath = createMockJar(tempDir.resolve("MyCoolMod-1.0.jar"), Map.of(
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0"
            ));

            ModLanguageFile result = adapter.extract(jarPath);

            assertEquals("mycoolmod-1.0", result.modId());
        }
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("throws RuntimeException when en_us.json contains corrupt JSON")
        void throwsRuntimeException_whenJsonIsCorrupt() throws IOException {
            Path jarPath = createMockJar(tempDir.resolve("corruptjson.jar"), Map.of(
                    "assets/corruptmod/lang/en_us.json", "this is not valid json"
            ));

            assertThrows(RuntimeException.class,
                    () -> adapter.extract(jarPath));
        }

        @Test
        @DisplayName("throws RuntimeException when jar is not a valid zip")
        void throwsRuntimeException_whenJarIsNotValidZip() throws IOException {
            Path invalidPath = tempDir.resolve("notazip.jar");
            Files.writeString(invalidPath, "this is not a zip file");

            assertThrows(RuntimeException.class,
                    () -> adapter.extract(invalidPath));
        }
    }
}
