package com.lucalzt.mctranslator.infrastructure.outbound.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.ports.outbound.ResourcePackGeneratorPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Adaptador de salida encargado de escribir físicamente las traducciones en español
 * en la estructura jerárquica estandarizada de un Resource Pack de Minecraft.
 * * Implementa ResourcePackGeneratorPort.
 */
public class MinecraftResourcePackAdapter implements ResourcePackGeneratorPort {

    private static final System.Logger LOGGER = System.getLogger(MinecraftResourcePackAdapter.class.getName());
    private static final int DEFAULT_PACK_FORMAT = 15; // Formato compatible con Minecraft 1.20 - 1.21.1+

    private final ObjectMapper objectMapper;

    /**
     * Construye el adaptador inyectando el serializador JSON Jackson.
     *
     * @param objectMapper Instancia de Jackson para escribir los archivos de idioma.
     */
    public MinecraftResourcePackAdapter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "El ObjectMapper de Jackson no puede ser nulo");
    }

    @Override
    public void generate(String modId, TranslationResult result, Path resourcePacksPath) {
        Objects.requireNonNull(modId, "El identificador del mod no puede ser nulo");
        Objects.requireNonNull(result, "El resultado de la traducción no puede ser nulo");
        Objects.requireNonNull(resourcePacksPath, "La ruta de destino del Resource Pack no puede ser nula");

        try {
            // 1. Me aseguro de la existencia de pack.mcmeta en la raíz del Resource Pack
            ensurePackMetadataExists(resourcePacksPath);

            // 2. Resuelvo la ruta física del archivo es_es.json del mod específico
            // Estructura: <ResourcePack>/assets/<modid>/lang/es_es.json
            Path modLangDir = resourcePacksPath.resolve("assets").resolve(modId).resolve("lang");
            Files.createDirectories(modLangDir);

            Path esEsFile = modLangDir.resolve("es_es.json");

            // 3. Escribo las traducciones formateadas con sangría estética
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(esEsFile.toFile(), result.translatedTranslations());

            LOGGER.log(System.Logger.Level.DEBUG, "Traducción física persistida de forma exitosa en: {0}", esEsFile.toAbsolutePath());

        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Fallo de E/S al escribir en la estructura del Resource Pack para el mod: " + modId, e);
            throw new RuntimeException("Error de infraestructura de archivos al generar traducciones finales", e);
        }
    }

    @Override
    public boolean hasCompleteTranslation(String modId, Set<String> originalKeys, Path resourcePacksPath) {
        Objects.requireNonNull(modId, "El identificador del mod no puede ser nulo");
        Objects.requireNonNull(originalKeys, "El conjunto de claves originales no puede ser nulo");
        Objects.requireNonNull(resourcePacksPath, "La ruta del Resource Pack no puede ser nula");

        if (originalKeys.isEmpty()) {
            return false;
        }

        Path esEsFile = resourcePacksPath.resolve("assets").resolve(modId).resolve("lang/es_es.json");

        if (!Files.exists(esEsFile)) {
            return false;
        }

        try {
            Map<String, Object> existing = objectMapper.readValue(esEsFile.toFile(), new TypeReference<Map<String, Object>>() {});
            boolean complete = existing.keySet().containsAll(originalKeys);
            if (complete) {
                LOGGER.log(System.Logger.Level.INFO, "El archivo de traducción {0} ya contiene todas las claves. Omitiendo reprocesamiento.", esEsFile.toAbsolutePath());
            }
            return complete;
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "No se pudo leer el archivo de traducción existente en {0}. Se re-procesará.", esEsFile.toAbsolutePath());
            return false;
        }
    }

    private void ensurePackMetadataExists(Path resourcePacksPath) throws IOException {
        Files.createDirectories(resourcePacksPath);
        Path packMcmetaFile = resourcePacksPath.resolve("pack.mcmeta");

        if (!Files.exists(packMcmetaFile)) {
            LOGGER.log(System.Logger.Level.INFO, "No se encontró el archivo de metadatos maestro 'pack.mcmeta'. Creando de forma automática en: {0}", packMcmetaFile.toAbsolutePath());

            // Estructura oficial del pack.mcmeta de Minecraft
            Map<String, Object> metadata = Map.of(
                    "pack", Map.of(
                            "pack_format", DEFAULT_PACK_FORMAT,
                            "description", "Traducciones al Español generadas automáticamente por MCTranslator"
                    )
            );

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(packMcmetaFile.toFile(), metadata);
        }
    }
}
