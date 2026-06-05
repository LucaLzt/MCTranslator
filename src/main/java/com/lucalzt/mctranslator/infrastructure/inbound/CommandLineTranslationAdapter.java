package com.lucalzt.mctranslator.infrastructure.inbound;

import com.lucalzt.mctranslator.infrastructure.outbound.persistence.JsonCheckpointRepositoryAdapter;
import com.lucalzt.mctranslator.ports.inbound.TranslateModpackUseCase;
import org.jspecify.annotations.NonNull;
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
            required = true,
            paramLabel = "<RUTA>"
    )
    private Path modpackPath;

    private final TranslateModpackUseCase translateModpackUseCase;
    private final JsonCheckpointRepositoryAdapter checkpointRepository;

    public CommandLineTranslationAdapter(
            TranslateModpackUseCase translateModpackUseCase,
            JsonCheckpointRepositoryAdapter checkpointRepository
    ) {
        this.translateModpackUseCase = translateModpackUseCase;
        this.checkpointRepository = checkpointRepository;
    }

    @Override
    public void run(String... args) {
        if (args.length == 0) {
            return;
        }
        int exitCode = new CommandLine(this).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            Path absolutePath = modpackPath.toAbsolutePath();

            if (!absolutePath.toFile().exists()) {
                LOGGER.log(System.Logger.Level.ERROR, "La ruta especificada no existe: {0}", absolutePath);
                return 2;
            }

            LOGGER.log(System.Logger.Level.INFO, "Ruta del modpack recibida e identificada con \u00e9xito: '{}'", absolutePath);

            checkpointRepository.setModpackPath(absolutePath);
            LOGGER.log(System.Logger.Level.INFO, "Repositorio de checkpoints enlazado exitosamente al directorio del modpack.");

            translateModpackUseCase.execute(absolutePath.toString());

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
