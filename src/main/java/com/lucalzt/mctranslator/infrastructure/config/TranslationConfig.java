package com.lucalzt.mctranslator.infrastructure.config;

import com.lucalzt.mctranslator.infrastructure.outbound.ai.GroqRestClientAdapter;
import com.lucalzt.mctranslator.infrastructure.outbound.ai.OllamaRestClientAdapter;
import com.lucalzt.mctranslator.infrastructure.outbound.ai.pool.ApiKeyPoolManager;
import com.lucalzt.mctranslator.ports.outbound.TranslationEnginePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración de Spring encargada de registrar el motor de traducción activo.
 * * Provee soporte dinámico para Ollama y Groq con pool de llaves configurables.
 */
@Configuration
public class TranslationConfig {

    private static final System.Logger LOGGER = System.getLogger(TranslationConfig.class.getName());

    @Value("${mctranslator.engine:ollama}")
    private String activeEngine;

    @Value("${mctranslator.groq.keys:}")
    private String rawGroqKeys;

    /**
     * Construye y registra el bean único para el pool de llaves de Groq.
     * Separa las llaves configuradas por comas en las properties o variables de entorno.
     *
     * @return El administrador de pool rotatorio ApiKeyPoolManager.
     */
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

    /**
     * Resuelve y expone el Bean activo que implementará el puerto de salida TranslationEnginePort.
     *
     * @param ollamaAdapter El adaptador cliente de Ollama local.
     * @param groqAdapter El adaptador cliente de Groq en la nube.
     * @return El adaptador activo inyectado bajo la abstracción del puerto de salida.
     */
    @Bean
    @Primary
    public TranslationEnginePort translationEnginePort(
            OllamaRestClientAdapter ollamaAdapter,
            GroqRestClientAdapter groqAdapter
    ) {
        LOGGER.log(System.Logger.Level.INFO, "Resolviendo motor de traducción activo configurado en properties: '{0}'", activeEngine);

        if ("groq".equalsIgnoreCase(activeEngine)) {
            LOGGER.log(System.Logger.Level.INFO, "Motor Groq seleccionado y cargado con éxito para el pipeline");
            return groqAdapter;
        }

        if ("ollama".equalsIgnoreCase(activeEngine)) {
            LOGGER.log(System.Logger.Level.INFO, "Motor Ollama seleccionado y cargado con éxito para el pipeline");
            return ollamaAdapter;
        }

        LOGGER.log(System.Logger.Level.ERROR, "El motor de traducción configurado '{}' no está soportado actualmente en esta compilación.", activeEngine);
        throw new IllegalArgumentException("No se pudo configurar el motor de IA. Motor no soportado: " + activeEngine);
    }
}
