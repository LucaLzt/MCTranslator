package com.lucalzt.mctranslator.infrastructure.outbound.quest;

import com.lucalzt.mctranslator.domain.model.QuestSystemType;
import com.lucalzt.mctranslator.domain.service.QuestFileDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("QuestFileDetectorImpl - filesystem detection")
class QuestFileDetectorImplTest {

    private final QuestFileDetector detector = new QuestFileDetectorImpl();

    @Nested
    @DisplayName("FTB Quests Modern")
    class FtbQuestsModern {

        @Test
        @DisplayName("detects modern when lang directory exists")
        void detectsModern_whenLangDirExists(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config/ftbquests/quests/lang"));

            assertEquals(QuestSystemType.FTB_QUESTS_MODERN, detector.detect(tempDir));
        }

        @Test
        @DisplayName("prefers modern over legacy when both exist")
        void prefersModernOverLegacy(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config/ftbquests/quests/lang"));
            Files.createDirectories(tempDir.resolve("config/ftbquests/quests/chapters"));

            assertEquals(QuestSystemType.FTB_QUESTS_MODERN, detector.detect(tempDir));
        }

        @Test
        @DisplayName("detects modern with en_us.snbt file")
        void detectsModern_withEnUsFile(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config/ftbquests/quests/lang"));
            Files.createFile(tempDir.resolve("config/ftbquests/quests/lang/en_us.snbt"));

            assertEquals(QuestSystemType.FTB_QUESTS_MODERN, detector.detect(tempDir));
        }
    }

    @Nested
    @DisplayName("FTB Quests Legacy")
    class FtbQuestsLegacy {

        @Test
        @DisplayName("detects legacy when chapters directory exists without lang")
        void detectsLegacy_whenChaptersDirExists(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config/ftbquests/quests/chapters"));

            assertEquals(QuestSystemType.FTB_QUESTS_LEGACY, detector.detect(tempDir));
        }

        @Test
        @DisplayName("detects legacy with files in chapters")
        void detectsLegacy_withChapterFiles(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config/ftbquests/quests/chapters"));
            Files.createFile(tempDir.resolve("config/ftbquests/quests/chapters/chapter1.snbt"));

            assertEquals(QuestSystemType.FTB_QUESTS_LEGACY, detector.detect(tempDir));
        }
    }

    @Nested
    @DisplayName("BetterQuesting")
    class BetterQuesting {

        @Test
        @DisplayName("detects BQ when QuestDatabase.json exists")
        void detectsBQ_whenQuestDatabaseExists(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config/betterquesting"));
            Files.createFile(tempDir.resolve("config/betterquesting/QuestDatabase.json"));

            assertEquals(QuestSystemType.BETTER_QUESTING, detector.detect(tempDir));
        }

        @Test
        @DisplayName("returns NONE when betterquesting dir exists but no json")
        void returnsNone_whenBqDirWithoutJson(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config/betterquesting"));

            assertEquals(QuestSystemType.NONE, detector.detect(tempDir));
        }
    }

    @Nested
    @DisplayName("No quest system")
    class NoQuestSystem {

        @Test
        @DisplayName("returns NONE with empty temp dir")
        void returnsNone_withEmptyDir(@TempDir Path tempDir) {
            assertEquals(QuestSystemType.NONE, detector.detect(tempDir));
        }

        @Test
        @DisplayName("returns NONE when config dir does not exist")
        void returnsNone_withoutConfig(@TempDir Path tempDir) {
            assertEquals(QuestSystemType.NONE, detector.detect(tempDir));
        }

        @Test
        @DisplayName("returns NONE for unrelated directory structure")
        void returnsNone_withUnrelatedDirs(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("config/someothermod"));

            assertEquals(QuestSystemType.NONE, detector.detect(tempDir));
        }
    }
}
