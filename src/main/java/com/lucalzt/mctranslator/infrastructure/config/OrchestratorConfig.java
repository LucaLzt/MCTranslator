package com.lucalzt.mctranslator.infrastructure.config;

import com.lucalzt.mctranslator.application.service.TranslationOrchestrator;
import com.lucalzt.mctranslator.domain.service.CheckpointFilter;
import com.lucalzt.mctranslator.domain.service.ChunkingService;
import com.lucalzt.mctranslator.domain.service.TranslationResultValidator;
import com.lucalzt.mctranslator.infrastructure.outbound.persistence.JsonCheckpointRepositoryAdapter;
import com.lucalzt.mctranslator.ports.inbound.TranslateModpackUseCase;
import com.lucalzt.mctranslator.ports.outbound.ModExtractorPort;
import com.lucalzt.mctranslator.ports.outbound.ResourcePackGeneratorPort;
import com.lucalzt.mctranslator.ports.outbound.TranslationEnginePort;
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

    @Value("${mctranslator.chunk-size:25}")
    private int maxChunkSize;

    /**
     * Construye y registra el caso de uso TranslateModpackUseCase.
     * Une los adaptadores de salida con los servicios utilitarios puros del dominio en la orquestación.
     *
     * @param modExtractor El extractor de archivos de idiomas de mods.
     * @param translationEngine El motor activo de traducción de IA (Ollama / Groq).
     * @param resourcePackGenerator El inyector de Resource Packs.
     * @param checkpointRepository El repositorio físico de checkpoints.
     * @return El Bean activo del orquestador del pipeline.
     */
    @Bean
    public TranslateModpackUseCase translateModpackUseCase(
            ModExtractorPort modExtractor,
            TranslationEnginePort translationEngine,
            ResourcePackGeneratorPort resourcePackGenerator,
            JsonCheckpointRepositoryAdapter checkpointRepository
    ) {
        LOGGER.log(System.Logger.Level.INFO, "Cableando e inicializando el caso de uso del dominio: TranslationOrchestrator con un tamaño de lote de {} claves.", maxChunkSize);

        // Instancio manualmente los servicios utilitarios de dominio
        ChunkingService chunkingService = new ChunkingService();
        CheckpointFilter checkpointFilter = new CheckpointFilter();
        TranslationResultValidator validator = new TranslationResultValidator();

        return new TranslationOrchestrator(
                modExtractor,
                translationEngine,
                resourcePackGenerator,
                checkpointRepository,
                chunkingService,
                checkpointFilter,
                validator,
                maxChunkSize
        );
    }
}
