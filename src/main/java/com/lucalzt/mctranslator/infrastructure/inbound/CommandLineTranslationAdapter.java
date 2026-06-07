package com.lucalzt.mctranslator.infrastructure.inbound;

import com.lucalzt.mctranslator.infrastructure.config.EngineRegistry;
import com.lucalzt.mctranslator.infrastructure.outbound.ai.GroqRestClientAdapter;
import com.lucalzt.mctranslator.infrastructure.outbound.ai.OllamaRestClientAdapter;
import com.lucalzt.mctranslator.infrastructure.outbound.persistence.JsonCheckpointRepositoryAdapter;
import com.lucalzt.mctranslator.ports.inbound.TranslateModpackUseCase;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(
        name = "mctranslator",
        mixinStandardHelpOptions = true,
        version = "MCTranslator 1.0.0",
        description = "Traduce los archivos de idioma de un modpack de Minecraft al espa\u00f1ol."
)
public class CommandLineTranslationAdapter implements CommandLineRunner, Callable<Integer> {

    private static final System.Logger LOGGER = System.getLogger(CommandLineTranslationAdapter.class.getName());

    @Spec
    private CommandSpec spec;

    @Option(
            names = "--modpack",
            description = "Ruta absoluta del directorio del modpack a procesar.",
            required = false,
            paramLabel = "<RUTA>"
    )
    private Path modpackPath;

    @Option(
            names = "--wizard",
            description = "Forzar modo interactivo aunque se hayan pasado argumentos.",
            required = false
    )
    private boolean forceWizard;

    private final TranslateModpackUseCase translateModpackUseCase;
    private final JsonCheckpointRepositoryAdapter checkpointRepository;
    private final InteractiveWizard wizard;
    private final EngineRegistry engineRegistry;

    public CommandLineTranslationAdapter(
            TranslateModpackUseCase translateModpackUseCase,
            JsonCheckpointRepositoryAdapter checkpointRepository,
            InteractiveWizard wizard,
            EngineRegistry engineRegistry
    ) {
        this.translateModpackUseCase = translateModpackUseCase;
        this.checkpointRepository = checkpointRepository;
        this.wizard = wizard;
        this.engineRegistry = engineRegistry;
    }

    @Override
    public void run(String... args) {
        int exitCode = new CommandLine(this).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    @Override
    public Integer call() {
        try {
            if (modpackPath == null || forceWizard) {
                TranslationConfigDTO cfg = wizard.promptUser();

                if ("ollama".equalsIgnoreCase(cfg.engine())) {
                    OllamaRestClientAdapter adapter = (OllamaRestClientAdapter) engineRegistry.get("ollama");
                    if (adapter != null) {
                        adapter.reconfigure(cfg.ollamaUrl(), cfg.ollamaModel());
                    }
                } else if ("groq".equalsIgnoreCase(cfg.engine())) {
                    GroqRestClientAdapter adapter = (GroqRestClientAdapter) engineRegistry.get("groq");
                    if (adapter != null) {
                        adapter.reconfigure(cfg.groqUrl(), cfg.groqModel(), cfg.groqKeys(),
                                cfg.groqRpm(), cfg.groqMaxTokens(), cfg.groqTpm());
                    }
                }

                Path absolutePath = Path.of(cfg.modpackPath()).toAbsolutePath();
                if (!absolutePath.toFile().exists()) {
                    LOGGER.log(System.Logger.Level.ERROR, "La ruta especificada no existe: {0}", absolutePath);
                    return 2;
                }

                checkpointRepository.setModpackPath(absolutePath);
                LOGGER.log(System.Logger.Level.INFO, "Repositorio de checkpoints enlazado exitosamente al directorio del modpack.");

                translateModpackUseCase.execute(cfg.modpackPath(), cfg);
            } else {
                Path absolutePath = modpackPath.toAbsolutePath();

                if (!absolutePath.toFile().exists()) {
                    LOGGER.log(System.Logger.Level.ERROR, "La ruta especificada no existe: {0}", absolutePath);
                    return 2;
                }

                LOGGER.log(System.Logger.Level.INFO, "Ruta del modpack recibida e identificada con \u00e9xito: '{}'", absolutePath);

                checkpointRepository.setModpackPath(absolutePath);
                LOGGER.log(System.Logger.Level.INFO, "Repositorio de checkpoints enlazado exitosamente al directorio del modpack.");

                translateModpackUseCase.execute(absolutePath.toString());
            }

            LOGGER.log(System.Logger.Level.INFO, "==================================================================");
            LOGGER.log(System.Logger.Level.INFO, "\u00a1PROCESO COMPLETADO! Las traducciones se han generado con \u00e9xito.");
            LOGGER.log(System.Logger.Level.INFO, "==================================================================");
            return 0;

        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Fallo cr\u00edtico en la ejecuci\u00f3n del pipeline de traducci\u00f3n.", e);
            return 1;
        }
    }
}
