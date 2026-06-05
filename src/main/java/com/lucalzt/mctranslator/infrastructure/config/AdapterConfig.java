package com.lucalzt.mctranslator.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.infrastructure.outbound.file.MinecraftResourcePackAdapter;
import com.lucalzt.mctranslator.infrastructure.outbound.file.ZipFileSystemExtractorAdapter;
import com.lucalzt.mctranslator.infrastructure.outbound.persistence.JsonCheckpointRepositoryAdapter;
import com.lucalzt.mctranslator.ports.outbound.ModExtractorPort;
import com.lucalzt.mctranslator.ports.outbound.ResourcePackGeneratorPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clase de configuración de Spring encargada de instanciar y registrar los adaptadores
 * de salida físicos (E/S de archivos y base de datos) bajo sus respectivas abstracciones de puertos.
 */
@Configuration
public class AdapterConfig {

    private static final System.Logger LOGGER = System.getLogger(AdapterConfig.class.getName());

    /**
     * Registra el extractor de archivos empaquetados JAR de mods de Minecraft.
     *
     * @param objectMapper Instancia global de Jackson para deserializar JSON.
     * @return El adaptador ZipFileSystemExtractorAdapter.
     */
    @Bean
    public ModExtractorPort modExtractorPort(ObjectMapper objectMapper) {
        LOGGER.log(System.Logger.Level.INFO, "Inicializando adaptador de infraestructura: ZipFileSystemExtractorAdapter");
        return new ZipFileSystemExtractorAdapter(objectMapper);
    }

    /**
     * Registra el generador físico del Resource Pack de Minecraft.
     *
     * @param objectMapper Instancia de Jackson para estructurar los archivos de idioma es_es.json.
     * @return El adaptador MinecraftResourcePackAdapter.
     */
    @Bean
    public ResourcePackGeneratorPort resourcePackGeneratorPort(ObjectMapper objectMapper) {
        LOGGER.log(System.Logger.Level.INFO, "Inicializando adaptador de infraestructura: MinecraftResourcePackAdapter");
        return new MinecraftResourcePackAdapter(objectMapper);
    }

    /**
     * Registra de forma explícita el adaptador concreto de persistencia de checkpoints.
     * Se expone directamente bajo su implementación concreta para permitir que el adaptador CLI
     * configure la ruta dinámica del modpack durante el arranque de la sesión.
     *
     * @param objectMapper Instancia de Jackson para procesar los ficheros JSON parciales.
     * @return El adaptador concreto JsonCheckpointRepositoryAdapter.
     */
    @Bean
    public JsonCheckpointRepositoryAdapter checkpointRepositoryAdapter(ObjectMapper objectMapper) {
        LOGGER.log(System.Logger.Level.INFO, "Inicializando adaptador de infraestructura: JsonCheckpointRepositoryAdapter");
        return new JsonCheckpointRepositoryAdapter(objectMapper);
    }
}
