package com.lucalzt.mctranslator.infrastructure.config;

import com.lucalzt.mctranslator.application.service.TranslationOrchestrator;
import com.lucalzt.mctranslator.domain.service.CheckpointFilter;
import com.lucalzt.mctranslator.domain.service.ChunkingService;
import com.lucalzt.mctranslator.domain.service.TranslationResultValidator;
import com.lucalzt.mctranslator.infrastructure.outbound.persistence.JsonCheckpointRepositoryAdapter;
import com.lucalzt.mctranslator.ports.inbound.TranslateModpackUseCase;
import com.lucalzt.mctranslator.ports.outbound.GlossaryPort;
import com.lucalzt.mctranslator.ports.outbound.ModExtractorPort;
import com.lucalzt.mctranslator.ports.outbound.ResourcePackGeneratorPort;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clase de configuración encargada de realizar el cableado (wiring) del caso de uso principal.
 * * Instancia y expone el orquestador puro de la capa de aplicación inyectando puertos de infraestructura.
 * * Inicializa manualmente los servicios de dominio de lógica pura sin contaminarlos con anotaciones de Spring.
 */
@Configuration
public class OrchestratorConfig {

    private static final System.Logger LOGGER = System.getLogger(OrchestratorConfig.class.getName());

    /**
     * Construye y registra el caso de uso TranslateModpackUseCase.
     * Une los adaptadores de salida con el registro de motores en la orquestación.
     *
     * @param modExtractor El extractor de archivos de idiomas de mods.
     * @param resourcePackGenerator El inyector de Resource Packs.
     * @param checkpointRepository El repositorio físico de checkpoints.
     * @param engineRegistry El registro dinámico de motores de traducción.
     * @return El Bean activo del orquestador del pipeline.
     */
    @Bean
    public TranslateModpackUseCase translateModpackUseCase(
            ModExtractorPort modExtractor,
            ResourcePackGeneratorPort resourcePackGenerator,
            JsonCheckpointRepositoryAdapter checkpointRepository,
            EngineRegistry engineRegistry,
            Optional<GlossaryPort> glossaryPort,
            @Value("${mctranslator.engine:ollama}") String defaultEngine,
            @Value("${mctranslator.chunk-size:50}") int defaultChunkSize
    ) {
        LOGGER.log(System.Logger.Level.INFO, "Cableando e inicializando TranslationOrchestrator con engine por defecto '{}' y lote de {} claves.", defaultEngine, defaultChunkSize);

        ChunkingService chunkingService = new ChunkingService();
        CheckpointFilter checkpointFilter = new CheckpointFilter();
        TranslationResultValidator validator = new TranslationResultValidator();

        return new TranslationOrchestrator(
                modExtractor,
                resourcePackGenerator,
                checkpointRepository,
                chunkingService,
                checkpointFilter,
                validator,
                engineRegistry,
                glossaryPort.orElse(null),
                defaultEngine,
                defaultChunkSize
        );
    }
}
