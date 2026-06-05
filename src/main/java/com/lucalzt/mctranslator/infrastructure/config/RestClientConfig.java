package com.lucalzt.mctranslator.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Clase de infraestructura de Spring que configura y expone el cliente HTTP unificado (RestClient).
 * * Utiliza el cliente HTTP nativo de Java 21 adaptado para hilos virtuales (Virtual Threads).
 * * Establece límites de tiempo de espera (timeouts) holgados adecuados para llamadas de inteligencia artificial.
 */
@Configuration
public class RestClientConfig {

    private static final System.Logger LOGGER = System.getLogger(RestClientConfig.class.getName());

    @Value("${mctranslator.ai.timeout-seconds:90}")
    private int timeoutSeconds;

    /**
     * Construye y expone un RestClient.Builder personalizado con la fábrica de peticiones HTTP de Java.
     *
     * @return El constructor de clientes HTTP preconfigurado con timeouts de lectura y conexión.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        LOGGER.log(System.Logger.Level.INFO, "Inicializando configuración de red de RestClient con timeouts de lectura establecidos en {} segundos.", timeoutSeconds);

        // Uso el HttpClient nativo de Java 21 (compatible con hilos virtuales)
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return RestClient.builder()
                .requestFactory(requestFactory);
    }
}
