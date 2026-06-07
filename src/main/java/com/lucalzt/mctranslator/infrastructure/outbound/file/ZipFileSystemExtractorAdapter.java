package com.lucalzt.mctranslator.infrastructure.outbound.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.model.ModLanguageFile;
import com.lucalzt.mctranslator.ports.outbound.ModExtractorPort;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Adaptador de salida encargado de montar y extraer archivos JAR de mods bajo demanda.
 * Cada llamada a extract() abre, parsea y retorna el contenido del JAR solicitado sin cache.
 */
public class ZipFileSystemExtractorAdapter implements ModExtractorPort {

    private static final System.Logger LOGGER = System.getLogger(ZipFileSystemExtractorAdapter.class.getName());

    private final ObjectMapper objectMapper;

    /**
     * Construye el extractor inyectando el deserializador JSON global de la aplicación.
     *
     * @param objectMapper Instancia de Jackson de Spring Boot.
     */
    public ZipFileSystemExtractorAdapter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "El ObjectMapper de Jackson no puede ser nulo");
    }

    @Override
    public ModLanguageFile extract(Path jarPath) {
        Objects.requireNonNull(jarPath, "La ruta del archivo .jar no puede ser nula");

        if (!Files.exists(jarPath)) {
            throw new IllegalArgumentException("El archivo físico de mod no existe en la ruta: " + jarPath);
        }

        return extractSingleJar(jarPath);
    }

    private ModLanguageFile extractSingleJar(Path jarPath) {
        LOGGER.log(System.Logger.Level.DEBUG, "Abriendo sistema de archivos virtual para el archivo JAR: {0}", jarPath.getFileName());

        try (FileSystem fileSystem = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
            Path assetsPath = fileSystem.getPath("/assets");

            if (!Files.exists(assetsPath) || !Files.isDirectory(assetsPath)) {
                return new ModLanguageFile(resolveFilenameAsFallbackId(jarPath), "en_us", Collections.emptyMap());
            }

            try (var directories = Files.list(assetsPath)) {
                for (Path candidateDir : directories.filter(Files::isDirectory).toList()) {
                    Path enUsLangFile = candidateDir.resolve("lang/en_us.json");

                    if (Files.exists(enUsLangFile)) {
                        String modId = candidateDir.getFileName().toString().replace("/", "");

                        try (InputStream is = Files.newInputStream(enUsLangFile)) {
                            Map<String, Object> raw = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
                            Map<String, String> translations = new HashMap<>();
                            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                                Object value = entry.getValue();
                                translations.put(entry.getKey(), value instanceof String s ? s : String.valueOf(value));
                            }
                            return new ModLanguageFile(modId, "en_us", translations);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Error de E/S al leer la estructura ZIP/JAR del mod: " + jarPath.getFileName(), e);
            throw new RuntimeException("No se pudo extraer la información de localización del mod empaquetado", e);
        }

        return new ModLanguageFile(resolveFilenameAsFallbackId(jarPath), "en_us", Collections.emptyMap());
    }

    private String resolveFilenameAsFallbackId(Path jarPath) {
        String name = jarPath.getFileName().toString();
        int extIdx = name.lastIndexOf('.');
        String cleanId = extIdx > 0 ? name.substring(0, extIdx) : name;
        return cleanId.toLowerCase().replaceAll("[^a-z0-9_.-]", "");
    }
}
