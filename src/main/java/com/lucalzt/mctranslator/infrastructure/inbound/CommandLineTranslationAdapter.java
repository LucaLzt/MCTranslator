package com.lucalzt.mctranslator.infrastructure.inbound;

import com.lucalzt.mctranslator.application.service.TranslationOrchestrator;
import com.lucalzt.mctranslator.infrastructure.config.EngineRegistry;
import com.lucalzt.mctranslator.infrastructure.inbound.glossary.GlossaryCommand;
import com.lucalzt.mctranslator.infrastructure.outbound.ai.GroqRestClientAdapter;
import com.lucalzt.mctranslator.infrastructure.outbound.ai.OllamaRestClientAdapter;
import com.lucalzt.mctranslator.infrastructure.outbound.persistence.JsonCheckpointRepositoryAdapter;
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
        description = "Traduce los archivos de idioma de un modpack de Minecraft al español."
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

    @Option(
            names = "--quests-only",
            description = "Traducir solo las misiones (quests), omitiendo mods.",
            required = false
    )
    private boolean questsOnly;

    @Option(
            names = "--mods-only",
            description = "Traducir solo los mods, omitiendo misiones (quests).",
            required = false
    )
    private boolean modsOnly;

    private final TranslationOrchestrator translationOrchestrator;
    private final JsonCheckpointRepositoryAdapter checkpointRepository;
    private final InteractiveWizard wizard;
    private final EngineRegistry engineRegistry;

    public CommandLineTranslationAdapter(
            TranslationOrchestrator translationOrchestrator,
            JsonCheckpointRepositoryAdapter checkpointRepository,
            InteractiveWizard wizard,
            EngineRegistry engineRegistry
    ) {
        this.translationOrchestrator = translationOrchestrator;
        this.checkpointRepository = checkpointRepository;
        this.wizard = wizard;
        this.engineRegistry = engineRegistry;
    }

    /**
     * Punto de entrada del CLI invocado por Spring Boot después de inicializar
     * el contexto. Construye el árbol de comandos Picocli con el comando raíz
     * {@code mctranslator} y registra {@link GlossaryCommand} como subcomando
     * para que esté disponible como {@code mctranslator glossary ...}.
     */
    @Override
    public void run(String... args) {
        int exitCode = new CommandLine(this)
                .addSubcommand(new GlossaryCommand())
                .execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    @Override
    public Integer call() {
        try {
            if (questsOnly && modsOnly) {
                LOGGER.log(System.Logger.Level.ERROR, "Las opciones --quests-only y --mods-only no pueden usarse juntas.");
                return 2;
            }

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

                translationOrchestrator.setGlossaryPath(absolutePath);
                LOGGER.log(System.Logger.Level.INFO, "Glosario persistente enlazado al directorio del modpack.");

                translationOrchestrator.execute(cfg.modpackPath(), cfg);
            } else {
                Path absolutePath = modpackPath.toAbsolutePath();

                if (!absolutePath.toFile().exists()) {
                    LOGGER.log(System.Logger.Level.ERROR, "La ruta especificada no existe: {0}", absolutePath);
                    return 2;
                }

                LOGGER.log(System.Logger.Level.INFO, "Ruta del modpack recibida e identificada con éxito: '{}'", absolutePath);

                checkpointRepository.setModpackPath(absolutePath);
                LOGGER.log(System.Logger.Level.INFO, "Repositorio de checkpoints enlazado exitosamente al directorio del modpack.");

                translationOrchestrator.setGlossaryPath(absolutePath);

                TranslationConfigDTO cfg = new TranslationConfigDTO(
                        absolutePath.toString(), null, null,
                        null, null, null, null, null,
                        null, null, null,
                        questsOnly ? true : null, modsOnly ? true : null
                );
                translationOrchestrator.execute(absolutePath.toString(), cfg);
            }

            LOGGER.log(System.Logger.Level.INFO, "==================================================================");
            LOGGER.log(System.Logger.Level.INFO, "¡PROCESO COMPLETADO! Las traducciones se han generado con éxito.");
            LOGGER.log(System.Logger.Level.INFO, "==================================================================");
            return 0;

        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Fallo crítico en la ejecución del pipeline de traducción.", e);
            return 1;
        }
    }
}
