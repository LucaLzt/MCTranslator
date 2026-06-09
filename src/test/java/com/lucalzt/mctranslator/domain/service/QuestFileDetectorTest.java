package com.lucalzt.mctranslator.domain.service;

import com.lucalzt.mctranslator.domain.model.QuestSystemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QuestFileDetector - system type detection")
class QuestFileDetectorTest {

    private final QuestFileDetector detector = modpackPath -> {
        Path config = modpackPath.resolve("config");

        if (!Files.isDirectory(config)) {
            return QuestSystemType.NONE;
        }

        Path ftbLang = config.resolve("ftbquests/quests/lang/en_us.snbt");
        if (Files.isRegularFile(ftbLang)) {
            return QuestSystemType.FTB_QUESTS_MODERN;
        }

        Path ftbChapters = config.resolve("ftbquests/quests/chapters");
        if (Files.isDirectory(ftbChapters)) {
            return QuestSystemType.FTB_QUESTS_LEGACY;
        }

        Path bqDb = config.resolve("betterquesting/QuestDatabase.json");
        if (Files.isRegularFile(bqDb)) {
            return QuestSystemType.BETTER_QUESTING;
        }

        return QuestSystemType.NONE;
    };

    @Nested
    @DisplayName("FTB Quests Modern")
    class FtbQuestsModern {

        @Test
        @DisplayName("detects modern format when lang/en_us.snbt exists")
        void detectsModernFormat(@TempDir Path tempDir) throws IOException {
            Path langDir = tempDir.resolve("config/ftbquests/quests/lang");
            Files.createDirectories(langDir);
            Files.createFile(langDir.resolve("en_us.snbt"));

            assertEquals(QuestSystemType.FTB_QUESTS_MODERN, detector.detect(tempDir));
        }

        @Test
        @DisplayName("prefers modern over legacy when both exist")
        void prefersModernOverLegacy(@TempDir Path tempDir) throws IOException {
            Path langDir = tempDir.resolve("config/ftbquests/quests/lang");
            Files.createDirectories(langDir);
            Files.createFile(langDir.resolve("en_us.snbt"));

            Path chaptersDir = tempDir.resolve("config/ftbquests/quests/chapters");
            Files.createDirectories(chaptersDir);

            assertEquals(QuestSystemType.FTB_QUESTS_MODERN, detector.detect(tempDir));
        }

        @Test
        @DisplayName("returns NONE when lang dir exists but no snbt file")
        void langDirWithoutSnbt(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config/ftbquests/quests/lang"));

            assertEquals(QuestSystemType.NONE, detector.detect(tempDir));
        }
    }

    @Nested
    @DisplayName("FTB Quests Legacy")
    class FtbQuestsLegacy {

        @Test
        @DisplayName("detects legacy format when chapters dir exists without lang")
        void detectsLegacyFormat(@TempDir Path tempDir) throws IOException {
            Path chaptersDir = tempDir.resolve("config/ftbquests/quests/chapters");
            Files.createDirectories(chaptersDir);
            Files.createFile(chaptersDir.resolve("chapter1.snbt"));

            assertEquals(QuestSystemType.FTB_QUESTS_LEGACY, detector.detect(tempDir));
        }

        @Test
        @DisplayName("detects legacy with empty chapters dir")
        void emptyChaptersDir(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config/ftbquests/quests/chapters"));

            assertEquals(QuestSystemType.FTB_QUESTS_LEGACY, detector.detect(tempDir));
        }
    }

    @Nested
    @DisplayName("BetterQuesting")
    class BetterQuesting {

        @Test
        @DisplayName("detects BetterQuesting when QuestDatabase.json exists")
        void detectsBetterQuesting(@TempDir Path tempDir) throws IOException {
            Path bqDir = tempDir.resolve("config/betterquesting");
            Files.createDirectories(bqDir);
            Files.createFile(bqDir.resolve("QuestDatabase.json"));

            assertEquals(QuestSystemType.BETTER_QUESTING, detector.detect(tempDir));
        }

        @Test
        @DisplayName("returns NONE when betterquesting dir exists but no json")
        void bqDirWithoutJson(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config/betterquesting"));

            assertEquals(QuestSystemType.NONE, detector.detect(tempDir));
        }
    }

    @Nested
    @DisplayName("No quest system")
    class NoQuestSystem {

        @Test
        @DisplayName("returns NONE when no quest directories exist")
        void noQuestDirectories(@TempDir Path tempDir) {
            assertEquals(QuestSystemType.NONE, detector.detect(tempDir));
        }

        @Test
        @DisplayName("returns NONE when config dir does not exist")
        void noConfigDir(@TempDir Path tempDir) {
            assertEquals(QuestSystemType.NONE, detector.detect(tempDir));
        }

        @Test
        @DisplayName("returns NONE for empty config dir")
        void emptyConfigDir(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config"));
            assertEquals(QuestSystemType.NONE, detector.detect(tempDir));
        }

        @Test
        @DisplayName("returns NONE when config has unrelated directories")
        void unrelatedDirectories(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config/someothermod"));
            assertEquals(QuestSystemType.NONE, detector.detect(tempDir));
        }
    }
}
