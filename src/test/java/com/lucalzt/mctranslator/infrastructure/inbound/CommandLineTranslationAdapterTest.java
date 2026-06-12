package com.lucalzt.mctranslator.infrastructure.inbound;

import com.lucalzt.mctranslator.application.service.TranslationOrchestrator;
import com.lucalzt.mctranslator.infrastructure.config.EngineRegistry;
import com.lucalzt.mctranslator.infrastructure.outbound.persistence.JsonCheckpointRepositoryAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DisplayName("CommandLineTranslationAdapter - flag validation")
class CommandLineTranslationAdapterTest {

    @Test
    @DisplayName("returns error code 2 when both --quests-only and --mods-only are set")
    void returnsError_whenBothFlagsUsed() throws Exception {
        CommandLineTranslationAdapter adapter = new CommandLineTranslationAdapter(
                mock(TranslationOrchestrator.class),
                mock(JsonCheckpointRepositoryAdapter.class),
                mock(InteractiveWizard.class),
                mock(EngineRegistry.class)
        );

        Field questsOnlyField = CommandLineTranslationAdapter.class.getDeclaredField("questsOnly");
        questsOnlyField.setAccessible(true);
        questsOnlyField.set(adapter, true);

        Field modsOnlyField = CommandLineTranslationAdapter.class.getDeclaredField("modsOnly");
        modsOnlyField.setAccessible(true);
        modsOnlyField.set(adapter, true);

        int exitCode = adapter.call();
        assertEquals(2, exitCode);
    }
}
