package com.lucalzt.mctranslator.application.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lucalzt.mctranslator.domain.exception.SessionFatalException;
import com.lucalzt.mctranslator.domain.model.ModLanguageFile;
import com.lucalzt.mctranslator.domain.model.ModpackPathResolver;
import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.domain.service.CheckpointFilter;
import com.lucalzt.mctranslator.domain.service.ChunkingService;
import com.lucalzt.mctranslator.domain.service.TranslationResultValidator;
import com.lucalzt.mctranslator.infrastructure.config.EngineRegistry;
import com.lucalzt.mctranslator.infrastructure.inbound.TranslationConfigDTO;
import com.lucalzt.mctranslator.infrastructure.outbound.glossary.JsonGlossaryAdapter;
import com.lucalzt.mctranslator.ports.inbound.TranslateModpackUseCase;
import com.lucalzt.mctranslator.domain.model.QuestData;
import com.lucalzt.mctranslator.domain.model.QuestSystemType;
import com.lucalzt.mctranslator.domain.service.QuestFileDetector;
import com.lucalzt.mctranslator.ports.outbound.CheckpointRepositoryPort;
import com.lucalzt.mctranslator.ports.outbound.GlossaryPort;
import com.lucalzt.mctranslator.ports.outbound.ModExtractorPort;
import com.lucalzt.mctranslator.ports.outbound.QuestExtractorPort;
import com.lucalzt.mctranslator.ports.outbound.QuestWriterPort;
import com.lucalzt.mctranslator.ports.outbound.ResourcePackGeneratorPort;
import com.lucalzt.mctranslator.ports.outbound.TranslationEnginePort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Coordinador de la capa de aplicación encargado de orquestar el pipeline completo de traducción de mods.
 * * Implementa el caso de uso principal TranslateModpackUseCase.
 * * Depende exclusivamente de los puertos de salida (interfaces) y de los servicios lógicos de dominio.
 * * Soporta selección dinámica de motor de traducción vía EngineRegistry.
 */
public class TranslationOrchestrator implements TranslateModpackUseCase {

    private static final System.Logger LOGGER = System.getLogger(TranslationOrchestrator.class.getName());

    private final ModExtractorPort modExtractor;
    private final ResourcePackGeneratorPort resourcePackGenerator;
    private final CheckpointRepositoryPort checkpointRepository;

    private final ChunkingService chunkingService;
    private final CheckpointFilter checkpointFilter;
    private final TranslationResultValidator validator;

    private final EngineRegistry engineRegistry;
    private GlossaryPort glossaryAdapter;
    private final String defaultEngine;
    private final int defaultChunkSize;

    private final QuestFileDetector questFileDetector;
    private final QuestExtractorPort questExtractor;
    private final QuestWriterPort questWriter;

    /**
     * Construye un nuevo orquestador con soporte de motores dinámicos.
     */
    public TranslationOrchestrator(
            ModExtractorPort modExtractor,
            ResourcePackGeneratorPort resourcePackGenerator,
            CheckpointRepositoryPort checkpointRepository,
            ChunkingService chunkingService,
            CheckpointFilter checkpointFilter,
            TranslationResultValidator validator,
            EngineRegistry engineRegistry,
            String defaultEngine,
            int defaultChunkSize,
            QuestFileDetector questFileDetector,
            QuestExtractorPort questExtractor,
            QuestWriterPort questWriter
    ) {
        this.modExtractor = Objects.requireNonNull(modExtractor, "El puerto ModExtractorPort no puede ser nulo");
        this.resourcePackGenerator = Objects.requireNonNull(resourcePackGenerator, "El puerto ResourcePackGeneratorPort no puede ser nulo");
        this.checkpointRepository = Objects.requireNonNull(checkpointRepository, "El puerto CheckpointRepositoryPort no puede ser nulo");
        this.chunkingService = Objects.requireNonNull(chunkingService, "El servicio ChunkingService no puede ser nulo");
        this.checkpointFilter = Objects.requireNonNull(checkpointFilter, "El servicio CheckpointFilter no puede ser nulo");
        this.validator = Objects.requireNonNull(validator, "El validador de resultados no puede ser nulo");
        this.engineRegistry = Objects.requireNonNull(engineRegistry, "El registro de motores EngineRegistry no puede ser nulo");
        this.defaultEngine = Objects.requireNonNull(defaultEngine, "El nombre del motor por defecto no puede ser nulo");
        if (defaultChunkSize <= 0) {
            throw new IllegalArgumentException("El tamaño de lote por defecto debe ser mayor a cero");
        }
        this.defaultChunkSize = defaultChunkSize;
        this.questFileDetector = Objects.requireNonNull(questFileDetector, "QuestFileDetector no puede ser nulo");
        this.questExtractor = Objects.requireNonNull(questExtractor, "QuestExtractorPort no puede ser nulo");
        this.questWriter = Objects.requireNonNull(questWriter, "QuestWriterPort no puede ser nulo");
    }

    /**
     * Configura la ruta del modpack para habilitar el glosario persistente.
     * Crea internamente un {@link JsonGlossaryAdapter} que lee/escribe
     * {@code .mctranslator/glossary.json} dentro del directorio del modpack.
     * Si no se llama a este método, el pipeline traduce sin glosario.
     */
    public void setGlossaryPath(Path modpackPath) {
        this.glossaryAdapter = new JsonGlossaryAdapter(
                new JsonMapper().registerModule(new JavaTimeModule()),
                modpackPath
        );
    }

    @Override
    public void execute(String modpackPath) {
        execute(modpackPath, null);
    }

    @Override
    public void execute(String modpackPath, TranslationConfigDTO overrides) {
        LOGGER.log(System.Logger.Level.INFO, "Iniciando pipeline de traducción para el modpack en la ruta: {0}", modpackPath);

        boolean modsOnly = overrides != null && Boolean.TRUE.equals(overrides.modsOnly());
        boolean questsOnly = overrides != null && Boolean.TRUE.equals(overrides.questsOnly());

        String engineToUse = defaultEngine;
        if (overrides != null && overrides.engine() != null) {
            engineToUse = overrides.engine();
        }
        engineRegistry.select(engineToUse);

        int effectiveChunkSize = defaultChunkSize;
        if (overrides != null && overrides.chunkSize() != null) {
            effectiveChunkSize = overrides.chunkSize();
        }

        TranslationEnginePort activeEngine = engineRegistry.getActive();
        if (activeEngine == null) {
            throw new IllegalStateException("No hay un motor de traducción activo registrado en el EngineRegistry");
        }

        if (glossaryAdapter != null) {
            activeEngine = new GlossaryAwareTranslator(activeEngine, glossaryAdapter, new GlossaryContextBuilder());
            LOGGER.log(System.Logger.Level.INFO, "Glosario persistente activo - el motor de traducción está envuelto en GlossaryAwareTranslator.");
        }

        ModpackPathResolver pathResolver = new ModpackPathResolver(Path.of(modpackPath));

        if (!questsOnly) {
            processMods(pathResolver, activeEngine, effectiveChunkSize);
        }

        if (!modsOnly) {
            processQuests(pathResolver, activeEngine, effectiveChunkSize);
        }

        LOGGER.log(System.Logger.Level.INFO, "Pipeline de traducción finalizado con éxito.");
    }

    private void processMods(ModpackPathResolver pathResolver, TranslationEnginePort activeEngine, int maxChunkSize) {
        Path modsPath = pathResolver.getModsPath();

        if (!Files.exists(modsPath) || !Files.isDirectory(modsPath)) {
            LOGGER.log(System.Logger.Level.WARNING, "No se encontró el directorio físico de mods en la ruta: {0}. Omitiendo traducción de mods.", modsPath);
            return;
        }

        List<Path> jarFiles;
        try (var stream = Files.list(modsPath)) {
            jarFiles = stream
                    .filter(path -> path.toString().endsWith(".jar"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Fallo crítico al leer el directorio de mods en disco", e);
            throw new RuntimeException("No se pudo listar los archivos de mods para el pipeline", e);
        }

        LOGGER.log(System.Logger.Level.INFO, "Se identificaron {0} archivos de mods (.jar) para procesar.", jarFiles.size());

        for (Path jarPath : jarFiles) {
            processMod(jarPath, pathResolver, activeEngine, maxChunkSize);
        }

        LOGGER.log(System.Logger.Level.INFO, "Pipeline de traducción de mods finalizado con éxito.");
    }

    private void processQuests(ModpackPathResolver pathResolver, TranslationEnginePort activeEngine, int maxChunkSize) {
        Path modpackPath = pathResolver.modpackPath();

        QuestSystemType systemType = questFileDetector.detect(modpackPath);
        if (systemType == QuestSystemType.NONE) {
            LOGGER.log(System.Logger.Level.INFO, "No se detectó ningún sistema de misiones conocido. Omitiendo.");
            return;
        }

        LOGGER.log(System.Logger.Level.INFO, "Sistema de misiones detectado: {0}", systemType);

        QuestData questData = questExtractor.extract(modpackPath);
        if (questData.systemType() == QuestSystemType.NONE) {
            if (systemType != QuestSystemType.NONE) {
                LOGGER.log(System.Logger.Level.WARNING, "Sistema '{0}' detectado pero no hay un adaptador de extracción implementado. Omitiendo.", systemType);
            } else {
                LOGGER.log(System.Logger.Level.INFO, "No se encontraron archivos de misiones para traducir. Omitiendo.");
            }
            return;
        }
        if (questData.entries().isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO, "No se encontraron textos de misiones para traducir. Omitiendo.");
            return;
        }

        String checkpointId = "__quests__";
        Set<String> checkpointKeys = checkpointRepository.load(checkpointId);
        LOGGER.log(System.Logger.Level.DEBUG, "Progreso de checkpoint cargado para quests: {0} claves ya traducidas.", checkpointKeys.size());

        Map<String, String> pendingKeys = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : questData.entries().entrySet()) {
            if (!checkpointKeys.contains(entry.getKey())) {
                pendingKeys.put(entry.getKey(), entry.getValue());
            }
        }

        if (pendingKeys.isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO, "Todas las misiones ya están traducidas. Omitiendo.");
            return;
        }

        LOGGER.log(System.Logger.Level.INFO, "Traduciendo {0} textos de misiones.", pendingKeys.size());

        List<TranslationChunk> chunks = chunkingService.split(pendingKeys, maxChunkSize);
        LOGGER.log(System.Logger.Level.DEBUG, "Se segmentaron las misiones en {0} lotes.", chunks.size());

        Set<String> progressKeys = new HashSet<>(checkpointKeys);
        Map<String, String> allTranslations = new HashMap<>();

        for (TranslationChunk chunk : chunks) {
            LOGGER.log(System.Logger.Level.INFO, "Traduciendo lote de misiones {0}/{1} ({2} textos)...",
                    chunk.chunkId() + 1, chunks.size(), chunk.size());

            try {
                TranslationResult result = activeEngine.translate(chunk);
                validator.validate(chunk, result);

                for (var entry : result.translatedTranslations().entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isBlank()) {
                        allTranslations.put(entry.getKey(), entry.getValue());
                    }
                }

                progressKeys.addAll(allTranslations.keySet());
                checkpointRepository.save(checkpointId, progressKeys);

                questWriter.write(modpackPath, questData, allTranslations);

            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Error al traducir lote de misiones {0}. Continuando.", chunk.chunkId() + 1);
            }
        }

        LOGGER.log(System.Logger.Level.INFO, "Traducción de misiones completada: {0} textos traducidos.", allTranslations.size());
    }

    private void processMod(Path jarPath, ModpackPathResolver pathResolver, TranslationEnginePort activeEngine, int maxChunkSize) {
        String filename = jarPath.getFileName().toString();
        LOGGER.log(System.Logger.Level.INFO, "Procesando archivo de mod: {0}", filename);

        try {
            ModLanguageFile modLanguageFile = modExtractor.extract(jarPath);
            String modId = modLanguageFile.modId();

            if (modLanguageFile.translations().isEmpty()) {
                LOGGER.log(System.Logger.Level.INFO, "El mod '{0}' ({1}) no contiene un archivo de idioma en_us.json válido o está vacío. Omitiendo.", filename, modId);
                return;
            }

            if (resourcePackGenerator.hasCompleteTranslation(modId, modLanguageFile.translations().keySet(), pathResolver.getResourcePacksPath())) {
                LOGGER.log(System.Logger.Level.INFO, "El mod '{0}' ({1}) ya tiene un archivo de traducción completo en el Resource Pack. Omitiendo.", filename, modId);
                return;
            }

            Set<String> checkpointKeys = checkpointRepository.load(modId);
            LOGGER.log(System.Logger.Level.DEBUG, "Progreso de checkpoint cargado para '{0}': {1} claves ya traducidas.", modId, checkpointKeys.size());

            Map<String, String> pendingKeys = checkpointFilter.filter(modLanguageFile, checkpointKeys);
            if (pendingKeys.isEmpty()) {
                LOGGER.log(System.Logger.Level.INFO, "El mod '{0}' ({1}) ya se encuentra completamente traducido. Omitiendo.", filename, modId);
                return;
            }

            LOGGER.log(System.Logger.Level.INFO, "Traduciendo {0} de {1} claves totales para el mod '{2}' ({3}).",
                    pendingKeys.size(), modLanguageFile.translations().size(), filename, modId);

            List<TranslationChunk> chunks = chunkingService.split(pendingKeys, maxChunkSize);
            LOGGER.log(System.Logger.Level.DEBUG, "Se segmentaron las traducciones de '{0}' en {1} lotes.", modId, chunks.size());

            Set<String> progressKeys = new HashSet<>(checkpointKeys);
            Map<String, String> allTranslations = new HashMap<>();

            for (TranslationChunk chunk : chunks) {
                LOGGER.log(System.Logger.Level.INFO, "Traduciendo lote {0}/{1} ({2} claves) para el mod '{3}'...",
                        chunk.chunkId() + 1, chunks.size(), chunk.size(), modId);

                TranslationResult result = activeEngine.translate(chunk);

                validator.validate(chunk, result);

                Map<String, String> validTranslations = new HashMap<>();
                for (var entry : result.translatedTranslations().entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isBlank()) {
                        validTranslations.put(entry.getKey(), entry.getValue());
                    }
                }

                allTranslations.putAll(validTranslations);
                resourcePackGenerator.generate(modId, new TranslationResult(chunk.chunkId(), allTranslations, Instant.now()), pathResolver.getResourcePacksPath());

                progressKeys.addAll(validTranslations.keySet());
                checkpointRepository.save(modId, progressKeys);

                LOGGER.log(System.Logger.Level.DEBUG, "Progreso guardado con éxito en el checkpoint para el lote {0} del mod '{1}'.",
                        chunk.chunkId() + 1, modId);
            }

            LOGGER.log(System.Logger.Level.INFO, "Mod '{0}' ({1}) traducido y guardado exitosamente.", filename, modId);

        } catch (SessionFatalException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Error fatal de sesión al procesar el mod '" + filename + "'. El pipeline se detiene.");
            throw e;
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Error no controlado al procesar el mod '" + filename + "'. El pipeline continuará con el siguiente mod por resiliencia.", e);
        }
    }
}
