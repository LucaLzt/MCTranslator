package com.lucalzt.mctranslator.infrastructure.outbound.quest;

import com.lucalzt.mctranslator.domain.model.ModpackPathResolver;
import com.lucalzt.mctranslator.domain.model.QuestSystemType;
import com.lucalzt.mctranslator.domain.service.QuestFileDetector;

import java.nio.file.Files;
import java.nio.file.Path;

public class QuestFileDetectorImpl implements QuestFileDetector {

    @Override
    public QuestSystemType detect(Path modpackPath) {
        ModpackPathResolver resolver = new ModpackPathResolver(modpackPath);

        if (Files.isDirectory(resolver.getFtbQuestsLangPath())) {
            return QuestSystemType.FTB_QUESTS_MODERN;
        }

        if (Files.isDirectory(resolver.getFtbQuestsChaptersPath())) {
            return QuestSystemType.FTB_QUESTS_LEGACY;
        }

        Path betterQuestingDb = resolver.getBetterQuestingPath().resolve("QuestDatabase.json");
        if (Files.isRegularFile(betterQuestingDb)) {
            return QuestSystemType.BETTER_QUESTING;
        }

        return QuestSystemType.NONE;
    }
}
