package com.lucalzt.mctranslator.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Clase de infraestructura de Spring encargada de configurar y exponer el ObjectMapper de Jackson.
 * * Proporciona un bean primario personalizado y altamente resiliente para el procesamiento de JSON.
 */
@Configuration
public class JacksonConfig {

    private static final System.Logger LOGGER = System.getLogger(JacksonConfig.class.getName());

    /**
     * Define y expone de forma explícita el serializador de JSON unificado de la aplicación.
     *
     * @return ObjectMapper configurado para ignorar propiedades desconocidas y manejar fechas modernas.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        LOGGER.log(System.Logger.Level.INFO, "Creando y registrando el ObjectMapper personalizado para el pipeline de MCTranslator...");

        ObjectMapper mapper = new ObjectMapper();

        // 1. Registro el módulo para dar soporte nativo a tipos de Java 8+ como Instant, Duration, ZonedDateTime
        mapper.registerModule(new JavaTimeModule());

        // 2. Configuro la resiliencia ante cambios: No lanzar error si la respuesta del LLM trae campos extra
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 3. Fuerzo el formateo de marcas de tiempo a formato estándar ISO-8601 legible en lugar de milisegundos numéricos
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        LOGGER.log(System.Logger.Level.INFO, "ObjectMapper registrado de forma exitosa en el contexto de Spring!");
        return mapper;
    }
}
