package com.lucalzt.mctranslator.application.service;

import com.lucalzt.mctranslator.domain.model.ModLanguageFile;
import com.lucalzt.mctranslator.domain.model.ModpackPathResolver;
import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.domain.service.CheckpointFilter;
import com.lucalzt.mctranslator.domain.service.ChunkingService;
import com.lucalzt.mctranslator.domain.service.TranslationResultValidator;
import com.lucalzt.mctranslator.ports.inbound.TranslateModpackUseCase;
import com.lucalzt.mctranslator.ports.outbound.CheckpointRepositoryPort;
import com.lucalzt.mctranslator.ports.outbound.ModExtractorPort;
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
 */
public class TranslationOrchestrator implements TranslateModpackUseCase {

    private static final System.Logger LOGGER = System.getLogger(TranslationOrchestrator.class.getName());

    private final ModExtractorPort modExtractor;
    private final TranslationEnginePort translationEngine;
    private final ResourcePackGeneratorPort resourcePackGenerator;
    private final CheckpointRepositoryPort checkpointRepository;

    private final ChunkingService chunkingService;
    private final CheckpointFilter checkpointFilter;
    private final TranslationResultValidator validator;

    private final int maxChunkSize;

    /**
     * Construye un nuevo orquestador inyectando todas sus dependencias necesarias.
     */
    public TranslationOrchestrator(
            ModExtractorPort modExtractor,
            TranslationEnginePort translationEngine,
            ResourcePackGeneratorPort resourcePackGenerator,
            CheckpointRepositoryPort checkpointRepository,
            ChunkingService chunkingService,
            CheckpointFilter checkpointFilter,
            TranslationResultValidator validator,
            int maxChunkSize
    ) {
        this.modExtractor = Objects.requireNonNull(modExtractor, "El puerto ModExtractorPort no puede ser nulo");
        this.translationEngine = Objects.requireNonNull(translationEngine, "El puerto TranslationEnginePort no puede ser nulo");
        this.resourcePackGenerator = Objects.requireNonNull(resourcePackGenerator, "El puerto ResourcePackGeneratorPort no puede ser nulo");
        this.checkpointRepository = Objects.requireNonNull(checkpointRepository, "El puerto CheckpointRepositoryPort no puede ser nulo");
        this.chunkingService = Objects.requireNonNull(chunkingService, "El servicio ChunkingService no puede ser nulo");
        this.checkpointFilter = Objects.requireNonNull(checkpointFilter, "El servicio CheckpointFilter no puede ser nulo");
        this.validator = Objects.requireNonNull(validator, "El validador de resultados no puede ser nulo");

        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("El tamaño máximo de lote (maxChunkSize) debe ser estrictamente mayor a cero");
        }
        this.maxChunkSize = maxChunkSize;
    }

    @Override
    public void execute(String modpackPath) {
        LOGGER.log(System.Logger.Level.INFO, "Iniciando pipeline de traducción para el modpack en la ruta: {0}", modpackPath);

        ModpackPathResolver pathResolver = new ModpackPathResolver(Path.of(modpackPath));
        Path modsPath = pathResolver.getModsPath();

        if (!Files.exists(modsPath) || !Files.isDirectory(modsPath)) {
            LOGGER.log(System.Logger.Level.WARNING, "No se encontró el directorio físico de mods en la ruta: {0}. Omitiendo traducción de mods.", modsPath);
            return;
        }

        List<Path> jarFiles;
        try (var stream = Files.list(modsPath)) {
            jarFiles = stream
                    .filter(path -> path.toString().endsWith(".jar"))
                    .sorted() // Se procesa alfabéticamente para asegurar un orden de ejecución predecible
                    .toList();
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Fallo crítico al leer el directorio de mods en disco", e);
            throw new RuntimeException("No se pudo listar los archivos de mods para el pipeline", e);
        }

        LOGGER.log(System.Logger.Level.INFO, "Se identificaron {0} archivos de mods (.jar) para procesar.", jarFiles.size());

        for (Path jarPath : jarFiles) {
            processMod(jarPath, pathResolver);
        }

        LOGGER.log(System.Logger.Level.INFO, "Pipeline de traducción de mods finalizado con éxito.");
    }

    /**
     * Procesa la traducción individual de un archivo de mod empaquetado.
     * Contiene un bloque de salvaguarda para evitar que un mod corrupto interrumpa el pipeline completo.
     */
    private void processMod(Path jarPath, ModpackPathResolver pathResolver) {
        String filename = jarPath.getFileName().toString();
        LOGGER.log(System.Logger.Level.INFO, "Procesando archivo de mod: {0}", filename);

        try {
            // 1. Extraigo las claves originales de idioma (en_us.json)
            ModLanguageFile modLanguageFile = modExtractor.extract(jarPath);
            String modId = modLanguageFile.modId();

            if (modLanguageFile.translations().isEmpty()) {
                LOGGER.log(System.Logger.Level.INFO, "El mod '{0}' ({1}) no contiene un archivo de idioma en_us.json válido o está vacío. Omitiendo.", filename, modId);
                return;
            }

            // 2. Cargo el progreso del checkpoint anterior
            Set<String> checkpointKeys = checkpointRepository.load(modId);
            LOGGER.log(System.Logger.Level.DEBUG, "Progreso de checkpoint cargado para '{0}': {1} claves ya traducidas.", modId, checkpointKeys.size());

            // 3. Filtro las claves ya procesadas
            Map<String, String> pendingKeys = checkpointFilter.filter(modLanguageFile, checkpointKeys);
            if (pendingKeys.isEmpty()) {
                LOGGER.log(System.Logger.Level.INFO, "El mod '{0}' ({1}) ya se encuentra completamente traducido. Omitiendo.", filename, modId);
                return;
            }

            LOGGER.log(System.Logger.Level.INFO, "Traduciendo {0} de {1} claves totales para el mod '{2}' ({3}).",
                    pendingKeys.size(), modLanguageFile.translations().size(), filename, modId);

            // 4. Divido las claves en lotes (chunks) manejables
            List<TranslationChunk> chunks = chunkingService.split(pendingKeys, maxChunkSize);
            LOGGER.log(System.Logger.Level.DEBUG, "Se segmentaron las traducciones de '{0}' en {1} lotes.", modId, chunks.size());

            // Conjunto para realizar la consolidación en caliente de checkpoints
            Set<String> progressKeys = new HashSet<>(checkpointKeys);

            // Acumulador para consolidar todas las traducciones del mod en un solo mapa
            Map<String, String> allTranslations = new HashMap<>();

            for (TranslationChunk chunk : chunks) {
                LOGGER.log(System.Logger.Level.INFO, "Traduciendo lote {0}/{1} ({2} claves) para el mod '{3}'...",
                        chunk.chunkId() + 1, chunks.size(), chunk.size(), modId);

                // 5. Invoco al motor de IA para traducir el lote
                TranslationResult result = translationEngine.translate(chunk);

                // 6. Valido paridad e integridad lógica de la respuesta
                validator.validate(chunk, result);

                // 7. Acumulo las traducciones y persisto el mapa completo del mod en el Resource Pack
                allTranslations.putAll(result.translatedTranslations());
                resourcePackGenerator.generate(modId, new TranslationResult(chunk.chunkId(), allTranslations, Instant.now()), pathResolver.getResourcePacksPath());

                // 8. Actualizo acumulador local y consolidar progreso en el repositorio de checkpoints
                progressKeys.addAll(chunk.translationsToTranslate().keySet());
                checkpointRepository.save(modId, progressKeys);

                LOGGER.log(System.Logger.Level.DEBUG, "Progreso guardado con éxito en el checkpoint para el lote {0} del mod '{1}'.",
                        chunk.chunkId() + 1, modId);
            }

            LOGGER.log(System.Logger.Level.INFO, "Mod '{0}' ({1}) traducido y guardado exitosamente.", filename, modId);

        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Error no controlado al procesar el mod '" + filename + "'. El pipeline continuará con el siguiente mod por resiliencia.", e);
        }
    }
}
