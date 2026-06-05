package com.lucalzt.mctranslator.infrastructure.config;

import com.lucalzt.mctranslator.infrastructure.outbound.ai.OllamaRestClientAdapter;
import com.lucalzt.mctranslator.ports.outbound.TranslationEnginePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuración de Spring encargada de registrar el motor de traducción activo.
 * * Permite intercambiar de manera transparente la infraestructura de IA mediante properties.
 */
@Configuration
public class TranslationConfig {

    private static final System.Logger LOGGER = System.getLogger(TranslationConfig.class.getName());

    @Value("${mctranslator.engine:ollama}")
    private String activeEngine;

    /**
     * Resuelve y expone el Bean activo que implementará el puerto de salida TranslationEnginePort.
     *
     * @param ollamaAdapter El adaptador cliente de Ollama disponible en el contexto de Spring.
     * @return El adaptador activo inyectado bajo la abstracción del puerto de salida.
     */
    @Bean
    @Primary
    public TranslationEnginePort translationEnginePort(OllamaRestClientAdapter ollamaAdapter) {
        LOGGER.log(System.Logger.Level.INFO, "Resolviendo motor de traducción activo configurado en properties: '{}'", activeEngine);

        if ("ollama".equalsIgnoreCase(activeEngine)) {
            LOGGER.log(System.Logger.Level.INFO, "Motor Ollama seleccionado y cargado con éxito para el pipeline");
            return ollamaAdapter;
        }

        LOGGER.log(System.Logger.Level.ERROR, "El motor de traducción configurado '{}' no está soportado actualmente en esta compilación.", activeEngine);
        throw new IllegalArgumentException("No se pudo configurar el motor de IA. Motor no soportado: " + activeEngine);
    }
}
