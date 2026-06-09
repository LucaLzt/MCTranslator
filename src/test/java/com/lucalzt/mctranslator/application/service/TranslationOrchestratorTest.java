package com.lucalzt.mctranslator.application.service;

import com.lucalzt.mctranslator.domain.model.ModLanguageFile;
import com.lucalzt.mctranslator.domain.model.QuestData;
import com.lucalzt.mctranslator.domain.model.QuestSystemType;
import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.domain.service.CheckpointFilter;
import com.lucalzt.mctranslator.domain.service.ChunkingService;
import com.lucalzt.mctranslator.domain.service.QuestFileDetector;
import com.lucalzt.mctranslator.domain.service.TranslationResultValidator;
import com.lucalzt.mctranslator.infrastructure.config.EngineRegistry;
import com.lucalzt.mctranslator.ports.outbound.CheckpointRepositoryPort;
import com.lucalzt.mctranslator.ports.outbound.ModExtractorPort;
import com.lucalzt.mctranslator.ports.outbound.QuestExtractorPort;
import com.lucalzt.mctranslator.ports.outbound.QuestWriterPort;
import com.lucalzt.mctranslator.ports.outbound.ResourcePackGeneratorPort;
import com.lucalzt.mctranslator.ports.outbound.TranslationEnginePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("TranslationOrchestrator - integration tests with real domain services")
class TranslationOrchestratorTest {

    @TempDir
    Path tempDir;

    private ModExtractorPort modExtractor;
    private TranslationEnginePort translationEngine;
    private ResourcePackGeneratorPort resourcePackGenerator;
    private CheckpointRepositoryPort checkpointRepository;
    private QuestFileDetector questFileDetector;
    private QuestExtractorPort questExtractor;
    private QuestWriterPort questWriter;
    private TranslationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        modExtractor = mock(ModExtractorPort.class);
        translationEngine = mock(TranslationEnginePort.class);
        resourcePackGenerator = mock(ResourcePackGeneratorPort.class);
        checkpointRepository = mock(CheckpointRepositoryPort.class);
        questFileDetector = mock(QuestFileDetector.class);
        questExtractor = mock(QuestExtractorPort.class);
        questWriter = mock(QuestWriterPort.class);

        when(questFileDetector.detect(any())).thenReturn(QuestSystemType.NONE);
        when(questExtractor.extract(any())).thenReturn(new QuestData(QuestSystemType.NONE, Map.of(), new byte[0]));

        ChunkingService chunkingService = new ChunkingService();
        CheckpointFilter checkpointFilter = new CheckpointFilter();
        TranslationResultValidator validator = new TranslationResultValidator();

        EngineRegistry engineRegistry = new EngineRegistry();
        engineRegistry.register("test", translationEngine);
        engineRegistry.select("test");

        orchestrator = new TranslationOrchestrator(
                modExtractor, resourcePackGenerator, checkpointRepository,
                chunkingService, checkpointFilter, validator,
                engineRegistry, "test", 2,
                questFileDetector, questExtractor, questWriter
        );
    }

    private Path createJar(Path modsDir, String name) throws IOException {
        return Files.createFile(modsDir.resolve(name + ".jar"));
    }

    private ModLanguageFile modFile(String modId, String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("keysAndValues must be pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return new ModLanguageFile(modId, "en_us", map);
    }

    private TranslationResult result(int chunkId, String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("keysAndValues must be pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return new TranslationResult(chunkId, map, Instant.now());
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Construction")
    class Construction {

        private EngineRegistry mockEngineRegistry() {
            EngineRegistry reg = new EngineRegistry();
            reg.register("test", mock(TranslationEnginePort.class));
            reg.select("test");
            return reg;
        }

        @Test
        @DisplayName("throws NullPointerException when ModExtractorPort is null")
        void throwsNullPointerException_whenModExtractorIsNull() {
            assertThrows(NullPointerException.class, () -> new TranslationOrchestrator(
                    null, mock(), mock(), mock(), mock(), mock(), mockEngineRegistry(), "test", 2,
                    mock(), mock(), mock()));
        }

        @Test
        @DisplayName("throws NullPointerException when ResourcePackGeneratorPort is null")
        void throwsNullPointerException_whenResourcePackGeneratorIsNull() {
            assertThrows(NullPointerException.class, () -> new TranslationOrchestrator(
                    mock(), null, mock(), mock(), mock(), mock(), mockEngineRegistry(), "test", 2,
                    mock(), mock(), mock()));
        }

        @Test
        @DisplayName("throws NullPointerException when CheckpointRepositoryPort is null")
        void throwsNullPointerException_whenCheckpointRepositoryIsNull() {
            assertThrows(NullPointerException.class, () -> new TranslationOrchestrator(
                    mock(), mock(), null, mock(), mock(), mock(), mockEngineRegistry(), "test", 2,
                    mock(), mock(), mock()));
        }

        @Test
        @DisplayName("throws NullPointerException when ChunkingService is null")
        void throwsNullPointerException_whenChunkingServiceIsNull() {
            assertThrows(NullPointerException.class, () -> new TranslationOrchestrator(
                    mock(), mock(), mock(), null, mock(), mock(), mockEngineRegistry(), "test", 2,
                    mock(), mock(), mock()));
        }

        @Test
        @DisplayName("throws NullPointerException when CheckpointFilter is null")
        void throwsNullPointerException_whenCheckpointFilterIsNull() {
            assertThrows(NullPointerException.class, () -> new TranslationOrchestrator(
                    mock(), mock(), mock(), mock(), null, mock(), mockEngineRegistry(), "test", 2,
                    mock(), mock(), mock()));
        }

        @Test
        @DisplayName("throws NullPointerException when TranslationResultValidator is null")
        void throwsNullPointerException_whenValidatorIsNull() {
            assertThrows(NullPointerException.class, () -> new TranslationOrchestrator(
                    mock(), mock(), mock(), mock(), mock(), null, mockEngineRegistry(), "test", 2,
                    mock(), mock(), mock()));
        }

        @Test
        @DisplayName("throws NullPointerException when EngineRegistry is null")
        void throwsNullPointerException_whenEngineRegistryIsNull() {
            assertThrows(NullPointerException.class, () -> new TranslationOrchestrator(
                    mock(), mock(), mock(), mock(), mock(), mock(), null, "test", 2,
                    mock(), mock(), mock()));
        }

        @Test
        @DisplayName("throws NullPointerException when defaultEngine is null")
        void throwsNullPointerException_whenDefaultEngineIsNull() {
            assertThrows(NullPointerException.class, () -> new TranslationOrchestrator(
                    mock(), mock(), mock(), mock(), mock(), mock(), mockEngineRegistry(), null, 2,
                    mock(), mock(), mock()));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when defaultChunkSize is 0")
        void throwsIllegalArgumentException_whenDefaultChunkSizeIsZero() {
            assertThrows(IllegalArgumentException.class, () -> new TranslationOrchestrator(
                    mock(), mock(), mock(), mock(), mock(), mock(), mockEngineRegistry(), "test", 0,
                    mock(), mock(), mock()));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when defaultChunkSize is negative")
        void throwsIllegalArgumentException_whenDefaultChunkSizeIsNegative() {
            assertThrows(IllegalArgumentException.class, () -> new TranslationOrchestrator(
                    mock(), mock(), mock(), mock(), mock(), mock(), mockEngineRegistry(), "test", -1,
                    mock(), mock(), mock()));
        }
    }

    // -------------------------------------------------------------------------
    // Mods directory resolution
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Mods directory resolution")
    class ModsDirectoryResolution {

        @Test
        @DisplayName("logs warning and returns when mods directory does not exist")
        void logsWarningAndReturnsEarly_whenModsDirectoryDoesNotExist() throws IOException {
            orchestrator.execute(tempDir.toString());

            verifyNoInteractions(modExtractor, translationEngine, resourcePackGenerator, checkpointRepository);
        }

        @Test
        @DisplayName("does not process any mods when no jar files are found")
        void doesNotProcessAnyMods_whenNoJarFilesFound() throws IOException {
            Files.createDirectories(tempDir.resolve("mods"));

            orchestrator.execute(tempDir.toString());

            verifyNoInteractions(modExtractor, translationEngine, resourcePackGenerator, checkpointRepository);
        }

        @Test
        @DisplayName("skips non-jar files in mods directory")
        void doesNotProcessAnyMods_whenOnlyNonJarFilesExist() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Files.createFile(modsDir.resolve("readme.txt"));
            Files.createFile(modsDir.resolve("config.json"));

            orchestrator.execute(tempDir.toString());

            verifyNoInteractions(modExtractor, translationEngine, resourcePackGenerator, checkpointRepository);
        }
    }

    // -------------------------------------------------------------------------
    // Happy path - mod processing
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Happy path")
    class ModProcessingHappyPath {

        @Test
        @DisplayName("translates single chunk and saves checkpoint when all keys fit in one chunk")
        void translatesSingleChunkAndSavesCheckpoint_whenAllKeysFitInOneChunk() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Path jarPath = createJar(modsDir, "test_mod");

            ModLanguageFile modFile = modFile("testmod",
                    "key.1", "Hello One",
                    "key.2", "Hello Two");

            when(modExtractor.extract(jarPath)).thenReturn(modFile);
            when(checkpointRepository.load("testmod")).thenReturn(Set.of());

            when(translationEngine.translate(any())).thenAnswer(invocation -> {
                TranslationChunk chunk = invocation.getArgument(0);
                Map<String, String> translated = new LinkedHashMap<>();
                chunk.translationsToTranslate().forEach((k, v) -> translated.put(k, v + "_es"));
                return new TranslationResult(chunk.chunkId(), translated, Instant.now());
            });

            orchestrator.execute(tempDir.toString());

            var inOrder = inOrder(modExtractor, checkpointRepository, translationEngine, resourcePackGenerator);
            inOrder.verify(modExtractor).extract(jarPath);
            inOrder.verify(checkpointRepository).load("testmod");
            inOrder.verify(translationEngine).translate(argThat(c -> c.chunkId() == 0));
            inOrder.verify(resourcePackGenerator).generate(eq("testmod"), any(), any());
            inOrder.verify(checkpointRepository).save(eq("testmod"), eq(Set.of("key.1", "key.2")));
        }

        @Test
        @DisplayName("translates multiple chunks and saves checkpoint progressively when keys exceed chunk size")
        void translatesMultipleChunksAndSavesCheckpointProgressively_whenKeysExceedChunkSize() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Path jarPath = createJar(modsDir, "test_mod");

            ModLanguageFile modFile = modFile("testmod",
                    "key.1", "Hello One",
                    "key.2", "Hello Two",
                    "key.3", "Hello Three");

            when(modExtractor.extract(jarPath)).thenReturn(modFile);
            when(checkpointRepository.load("testmod")).thenReturn(Set.of());

            when(translationEngine.translate(any())).thenAnswer(invocation -> {
                TranslationChunk chunk = invocation.getArgument(0);
                Map<String, String> translated = new LinkedHashMap<>();
                chunk.translationsToTranslate().forEach((k, v) -> translated.put(k, v + "_es"));
                return new TranslationResult(chunk.chunkId(), translated, Instant.now());
            });

            orchestrator.execute(tempDir.toString());

            verify(modExtractor).extract(jarPath);
            verify(checkpointRepository).load("testmod");
            verify(translationEngine, times(2)).translate(any());
            verify(resourcePackGenerator, times(2)).generate(eq("testmod"), any(), any());
            verify(checkpointRepository, times(2)).save(eq("testmod"), any());
        }
    }

    // -------------------------------------------------------------------------
    // Checkpoint scenarios
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Checkpoint scenarios")
    class CheckpointScenarios {

        @Test
        @DisplayName("skips mod when all keys are already translated")
        void skipsMod_whenAllKeysAreAlreadyTranslated() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Path jarPath = createJar(modsDir, "test_mod");

            ModLanguageFile modFile = modFile("testmod",
                    "key.1", "Hello One",
                    "key.2", "Hello Two");

            when(modExtractor.extract(jarPath)).thenReturn(modFile);
            when(checkpointRepository.load("testmod")).thenReturn(Set.of("key.1", "key.2"));

            orchestrator.execute(tempDir.toString());

            verify(modExtractor).extract(jarPath);
            verify(checkpointRepository).load("testmod");
            verify(translationEngine, never()).translate(any());
            verify(resourcePackGenerator, never()).generate(any(), any(), any());
            verify(checkpointRepository, never()).save(any(), any());
        }

        @Test
        @DisplayName("processes only remaining keys when partial checkpoint exists")
        void processesOnlyRemainingKeys_whenPartialCheckpointExists() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Path jarPath = createJar(modsDir, "test_mod");

            ModLanguageFile modFile = modFile("testmod",
                    "key.1", "Hello One",
                    "key.2", "Hello Two",
                    "key.3", "Hello Three");

            when(modExtractor.extract(jarPath)).thenReturn(modFile);
            when(checkpointRepository.load("testmod")).thenReturn(Set.of("key.1"));

            when(translationEngine.translate(any())).thenAnswer(invocation -> {
                TranslationChunk chunk = invocation.getArgument(0);
                Map<String, String> translated = new LinkedHashMap<>();
                chunk.translationsToTranslate().forEach((k, v) -> translated.put(k, v + "_es"));
                return new TranslationResult(chunk.chunkId(), translated, Instant.now());
            });

            orchestrator.execute(tempDir.toString());

            verify(translationEngine).translate(argThat(c -> c.size() == 2));
            verify(checkpointRepository).save(eq("testmod"), eq(Set.of("key.1", "key.2", "key.3")));
        }

        @Test
        @DisplayName("skips mod when extracted translations are empty")
        void skipsMod_whenTranslationMapIsEmpty() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Path jarPath = createJar(modsDir, "test_mod");

            ModLanguageFile emptyFile = new ModLanguageFile("emptymod", "en_us", Map.of());

            when(modExtractor.extract(jarPath)).thenReturn(emptyFile);

            orchestrator.execute(tempDir.toString());

            verify(modExtractor).extract(jarPath);
            verify(checkpointRepository, never()).load(any());
            verify(translationEngine, never()).translate(any());
            verify(resourcePackGenerator, never()).generate(any(), any(), any());
        }
    }

    // -------------------------------------------------------------------------
    // Error recovery
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Error recovery")
    class ErrorRecovery {

        @Test
        @DisplayName("continues to next mod when extraction fails")
        void continuesToNextMod_whenExtractionFails() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Path jar1Path = createJar(modsDir, "fail_mod");
            Path jar2Path = createJar(modsDir, "ok_mod");

            ModLanguageFile modFile2 = modFile("okmod",
                    "key.1", "Hello");

            when(modExtractor.extract(jar1Path)).thenThrow(new RuntimeException("corrupt jar"));
            when(modExtractor.extract(jar2Path)).thenReturn(modFile2);
            when(checkpointRepository.load("okmod")).thenReturn(Set.of());
            when(translationEngine.translate(any())).thenAnswer(invocation -> {
                TranslationChunk chunk = invocation.getArgument(0);
                Map<String, String> translated = new LinkedHashMap<>();
                chunk.translationsToTranslate().forEach((k, v) -> translated.put(k, v + "_es"));
                return new TranslationResult(chunk.chunkId(), translated, Instant.now());
            });

            assertDoesNotThrow(() -> orchestrator.execute(tempDir.toString()));

            verify(modExtractor).extract(jar1Path);
            verify(modExtractor).extract(jar2Path);
            verify(translationEngine).translate(any());
            verify(resourcePackGenerator).generate(eq("okmod"), any(), any());
            verify(checkpointRepository).save(eq("okmod"), any());
        }

        @Test
        @DisplayName("continues to next mod when translation fails")
        void continuesToNextMod_whenTranslationFails() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Path jar1Path = createJar(modsDir, "fail_mod");
            Path jar2Path = createJar(modsDir, "ok_mod");

            ModLanguageFile modFile1 = modFile("failmod",
                    "key.1", "Hello");
            ModLanguageFile modFile2 = modFile("okmod",
                    "key.2", "World");

            when(modExtractor.extract(jar1Path)).thenReturn(modFile1);
            when(modExtractor.extract(jar2Path)).thenReturn(modFile2);
            when(checkpointRepository.load("failmod")).thenReturn(Set.of());
            when(checkpointRepository.load("okmod")).thenReturn(Set.of());

            when(translationEngine.translate(any()))
                    .thenThrow(new RuntimeException("API error"))
                    .thenAnswer(invocation -> {
                        TranslationChunk chunk = invocation.getArgument(0);
                        Map<String, String> translated = new LinkedHashMap<>();
                        chunk.translationsToTranslate().forEach((k, v) -> translated.put(k, v + "_es"));
                        return new TranslationResult(chunk.chunkId(), translated, Instant.now());
                    });

            assertDoesNotThrow(() -> orchestrator.execute(tempDir.toString()));

            verify(modExtractor).extract(jar1Path);
            verify(modExtractor).extract(jar2Path);
            verify(translationEngine, times(2)).translate(any());
            verify(resourcePackGenerator).generate(eq("okmod"), any(), any());
            verify(checkpointRepository, never()).save(eq("failmod"), any());
            verify(checkpointRepository).save(eq("okmod"), any());
        }

        @Test
        @DisplayName("continues to next mod when validation fails")
        void continuesToNextMod_whenValidationFails() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Path jar1Path = createJar(modsDir, "fail_mod");
            Path jar2Path = createJar(modsDir, "ok_mod");

            ModLanguageFile modFile1 = modFile("failmod",
                    "key.1", "Hello");
            ModLanguageFile modFile2 = modFile("okmod",
                    "key.2", "World");

            when(modExtractor.extract(jar1Path)).thenReturn(modFile1);
            when(modExtractor.extract(jar2Path)).thenReturn(modFile2);
            when(checkpointRepository.load("failmod")).thenReturn(Set.of());
            when(checkpointRepository.load("okmod")).thenReturn(Set.of());

            when(translationEngine.translate(any()))
                    .thenReturn(result(0, "key.1", ""))
                    .thenAnswer(invocation -> {
                        TranslationChunk chunk = invocation.getArgument(0);
                        Map<String, String> translated = new LinkedHashMap<>();
                        chunk.translationsToTranslate().forEach((k, v) -> translated.put(k, v + "_es"));
                        return new TranslationResult(chunk.chunkId(), translated, Instant.now());
                    });

            assertDoesNotThrow(() -> orchestrator.execute(tempDir.toString()));

            verify(modExtractor).extract(jar1Path);
            verify(modExtractor).extract(jar2Path);
            verify(translationEngine, times(2)).translate(any());
            verify(resourcePackGenerator).generate(eq("okmod"), any(), any());
            verify(checkpointRepository, never()).save(eq("failmod"), any());
            verify(checkpointRepository).save(eq("okmod"), any());
        }

        @Test
        @DisplayName("continues to next mod when resource pack generation fails")
        void continuesToNextMod_whenResourcePackGenerationFails() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Path jar1Path = createJar(modsDir, "fail_mod");
            Path jar2Path = createJar(modsDir, "ok_mod");

            ModLanguageFile modFile1 = modFile("failmod",
                    "key.1", "Hello");
            ModLanguageFile modFile2 = modFile("okmod",
                    "key.2", "World");

            when(modExtractor.extract(jar1Path)).thenReturn(modFile1);
            when(modExtractor.extract(jar2Path)).thenReturn(modFile2);
            when(checkpointRepository.load("failmod")).thenReturn(Set.of());
            when(checkpointRepository.load("okmod")).thenReturn(Set.of());

            when(translationEngine.translate(any())).thenAnswer(invocation -> {
                TranslationChunk chunk = invocation.getArgument(0);
                Map<String, String> translated = new LinkedHashMap<>();
                chunk.translationsToTranslate().forEach((k, v) -> translated.put(k, v + "_es"));
                return new TranslationResult(chunk.chunkId(), translated, Instant.now());
            });

            doThrow(new RuntimeException("disk full"))
                    .doNothing()
                    .when(resourcePackGenerator).generate(any(), any(), any());

            assertDoesNotThrow(() -> orchestrator.execute(tempDir.toString()));

            verify(modExtractor).extract(jar1Path);
            verify(modExtractor).extract(jar2Path);
            verify(translationEngine, times(2)).translate(any());
            verify(resourcePackGenerator, times(2)).generate(any(), any(), any());
            verify(checkpointRepository, never()).save(eq("failmod"), any());
            verify(checkpointRepository).save(eq("okmod"), any());
        }
    }

    // -------------------------------------------------------------------------
    // Multiple mods
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Multiple mods")
    class MultipleMods {

        @Test
        @DisplayName("processes mods in alphabetical order")
        void processesModsInAlphabeticalOrder() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Path jarZ = createJar(modsDir, "z_mod");
            Path jarA = createJar(modsDir, "a_mod");
            Path jarM = createJar(modsDir, "m_mod");

            ModLanguageFile modA = modFile("amod", "key.a", "Alpha");
            ModLanguageFile modM = modFile("mmod", "key.m", "Mu");
            ModLanguageFile modZ = modFile("zmod", "key.z", "Zeta");

            when(modExtractor.extract(jarA)).thenReturn(modA);
            when(modExtractor.extract(jarM)).thenReturn(modM);
            when(modExtractor.extract(jarZ)).thenReturn(modZ);
            when(checkpointRepository.load("amod")).thenReturn(Set.of());
            when(checkpointRepository.load("mmod")).thenReturn(Set.of());
            when(checkpointRepository.load("zmod")).thenReturn(Set.of());

            when(translationEngine.translate(any())).thenAnswer(invocation -> {
                TranslationChunk chunk = invocation.getArgument(0);
                Map<String, String> translated = new LinkedHashMap<>();
                chunk.translationsToTranslate().forEach((k, v) -> translated.put(k, v + "_es"));
                return new TranslationResult(chunk.chunkId(), translated, Instant.now());
            });

            orchestrator.execute(tempDir.toString());

            var inOrder = inOrder(modExtractor);
            inOrder.verify(modExtractor).extract(jarA);
            inOrder.verify(modExtractor).extract(jarM);
            inOrder.verify(modExtractor).extract(jarZ);
        }
    }
}
