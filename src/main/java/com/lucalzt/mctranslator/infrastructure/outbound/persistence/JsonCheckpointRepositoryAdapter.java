package com.lucalzt.mctranslator.infrastructure.outbound.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.model.ModpackPathResolver;
import com.lucalzt.mctranslator.ports.outbound.CheckpointRepositoryPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Adaptador de salida (Secondary Adapter) encargado de persistir y recuperar el progreso
 * de traducción mediante archivos JSON individuales en el sistema de archivos local.
 * * Implementa CheckpointRepositoryPort.
 */
public class JsonCheckpointRepositoryAdapter implements CheckpointRepositoryPort {

    private static final System.Logger LOGGER = System.getLogger(JsonCheckpointRepositoryAdapter.class.getName());
    private static final String CHECKPOINTS_DIR_NAME = ".checkpoints";

    private final ObjectMapper objectMapper;
    private Path modpackPath;

    /**
     * Construye el adaptador inyectando el deserializador JSON del framework.
     *
     * @param objectMapper Instancia de Jackson para procesar los checkpoints.
     */
    public JsonCheckpointRepositoryAdapter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "El ObjectMapper de Jackson no puede ser nulo");
    }

    /**
     * Configura dinámicamente la ruta base del modpack bajo el cual se ubicará el almacén de checkpoints.
     *
     * @param modpackPath Ruta física base del modpack activo.
     */
    public void setModpackPath(Path modpackPath) {
        this.modpackPath = Objects.requireNonNull(modpackPath, "La ruta del modpack no puede ser nula");
    }

    @Override
    public void save(String modId, Set<String> translatedKeys) {
        ensureModpackPathConfigured();
        Objects.requireNonNull(modId, "El identificador del mod no puede ser nulo");
        Objects.requireNonNull(translatedKeys, "El conjunto de claves a salvar no puede ser nulo");

        Path checkpointFile = resolveCheckpointFile(modId);

        try {
            // Me aseguro que toda la estructura de directorios exista antes de escribir
            Files.createDirectories(checkpointFile.getParent());

            CheckpointData data = new CheckpointData(modId, translatedKeys);
            objectMapper.writeValue(checkpointFile.toFile(), data);

            LOGGER.log(System.Logger.Level.DEBUG, "Progreso guardado: {0} claves guardadas para el mod '{1}' en {2}",
                    translatedKeys.size(), modId, checkpointFile.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Fallo al escribir el archivo de checkpoint JSON para el mod: " + modId, e);
            throw new RuntimeException("Error crítico de infraestructura de persistencia al guardar progreso", e);
        }
    }

    @Override
    public Set<String> load(String modId) {
        ensureModpackPathConfigured();
        Objects.requireNonNull(modId, "El identificador del mod no puede ser nulo");

        Path checkpointFile = resolveCheckpointFile(modId);

        if (!Files.exists(checkpointFile)) {
            LOGGER.log(System.Logger.Level.DEBUG, "No se encontró un archivo de checkpoint previo para el mod '{0}'. Iniciando progreso limpio.", modId);
            return Collections.emptySet();
        }

        try {
            CheckpointData data = objectMapper.readValue(checkpointFile.toFile(), CheckpointData.class);
            if (data == null || data.translatedKeys() == null) {
                return Collections.emptySet();
            }
            return Set.copyOf(data.translatedKeys());

        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "El archivo de checkpoint en {0} está corrupto o es ilegible. Se ignorará y se reanudará en blanco.", checkpointFile.toAbsolutePath(), e);
            return Collections.emptySet();
        }
    }

    private void ensureModpackPathConfigured() {
        if (this.modpackPath == null) {
            throw new IllegalStateException("El adaptador de persistencia no ha sido inicializado con la ruta del modpack (modpackPath). Llame a setModpackPath() antes de usar.");
        }
    }

    private Path resolveCheckpointFile(String modId) {
        ModpackPathResolver pathResolver = new ModpackPathResolver(this.modpackPath);
        return pathResolver.getResourcePacksPath()
                .resolve(CHECKPOINTS_DIR_NAME)
                .resolve(modId + ".json");
    }

    /**
     * Registro de datos interno (Value Object de Infraestructura) utilizado exclusivamente
     * para mapear la estructura física del archivo JSON de checkpoints.
     */
    private record CheckpointData(String modId, Set<String> translatedKeys) {}
}
