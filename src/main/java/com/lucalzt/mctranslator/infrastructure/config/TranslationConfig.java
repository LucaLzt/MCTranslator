package com.lucalzt.mctranslator.infrastructure.config;

import com.lucalzt.mctranslator.infrastructure.outbound.ai.pool.ApiKeyPoolManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración de Spring encargada de registrar el pool de llaves de API.
 * La selección del motor de traducción se delega a EngineRegistry en runtime.
 */
@Configuration
public class TranslationConfig {

    private static final System.Logger LOGGER = System.getLogger(TranslationConfig.class.getName());

    @Value("${mctranslator.groq.keys:}")
    private String rawGroqKeys;

    @Bean
    public ApiKeyPoolManager apiKeyPoolManager() {
        LOGGER.log(System.Logger.Level.INFO, "Inicializando ApiKeyPoolManager para el pipeline de traducción...");

        List<String> keysList;
        if (rawGroqKeys == null || rawGroqKeys.isBlank()) {
            LOGGER.log(System.Logger.Level.WARNING, "No se detectaron llaves en 'mctranslator.groq.keys'. Se cargará llave de prueba ficticia.");
            keysList = List.of("DUMMY_KEY");
        } else {
            keysList = Arrays.stream(rawGroqKeys.split(","))
                    .map(String::trim)
                    .filter(key -> !key.isEmpty())
                    .toList();
        }

        return new ApiKeyPoolManager(keysList);
    }
}
