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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Adaptador de salida encargado de montar y extraer los archivos JAR de mods en paralelo.
 * * Implementa caching inteligente y pre-fetch concurrente en el primer acceso a disco.
 * * Utiliza hilos virtuales (Virtual Threads) para paralelizar la lectura masiva de archivos.
 */
public class ZipFileSystemExtractorAdapter implements ModExtractorPort {

    private static final System.Logger LOGGER = System.getLogger(ZipFileSystemExtractorAdapter.class.getName());

    private final ObjectMapper objectMapper;
    private final Map<Path, ModLanguageFile> cache = new ConcurrentHashMap<>();
    private boolean preFetched = false;

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

        // Hago una pre-carga paralela y concurrente de todos los JARs del directorio en el primer acceso
        synchronized (this) {
            if (!preFetched) {
                preFetchAllJars(jarPath.getParent());
                preFetched = true;
            }
        }

        // Si ya fue extraído en paralelo, lo sirvo instantáneamente de la caché en memoria
        if (cache.containsKey(jarPath)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Retornando resultado pre-extraído desde la caché para: {0}", jarPath.getFileName());
            return cache.get(jarPath);
        }

        // Caso de respaldo (fallback) por si el archivo ingresó de forma tardía
        return extractSingleJar(jarPath);
    }

    /**
     * Escanea el directorio de mods y construye todos los archivos JAR de forma concurrente
     * utilizando un ejecutor de hilos virtuales.
     */
    private void preFetchAllJars(Path modsDirectory) {
        if (modsDirectory == null || !Files.exists(modsDirectory) || !Files.isDirectory(modsDirectory)) {
            return;
        }

        LOGGER.log(System.Logger.Level.INFO, "Iniciando extracción paralela de todos los JARs en: {0} usando hilos virtuales", modsDirectory);

        try (var stream = Files.list(modsDirectory)) {
            var jarFiles = stream
                    .filter(path -> path.toString().endsWith(".jar"))
                    .toList();

            if (jarFiles.isEmpty()) {
                return;
            }

            long startTime = System.currentTimeMillis();

            // Despliego hilos virtuales efímeros para abrir y leer los ZIPs simultáneamente
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (Path jar : jarFiles) {
                    executor.submit(() -> {
                        try {
                            ModLanguageFile result = extractSingleJar(jar);
                            cache.put(jar, result);
                        } catch (Exception e) {
                            LOGGER.log(System.Logger.Level.ERROR, "Error al pre-extraer de forma concurrente el JAR: " + jar.getFileName(), e);
                        }
                    });
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            LOGGER.log(System.Logger.Level.INFO, "Extracción paralela completada. {0} JARs leídos y cacheados en {1}ms", jarFiles.size(), duration);

        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Fallo crítico al pre-escanear directorio de mods para extracción paralela", e);
        }
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
