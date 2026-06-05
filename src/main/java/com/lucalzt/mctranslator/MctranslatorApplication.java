package com.lucalzt.mctranslator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada principal (Entry Point) de la aplicación Spring Boot.
 * * Ejecuta el escaneo de paquetes hexagonal de mctranslator.
 * * Inicializa la aplicación y cede el control al CommandLineTranslationAdapter.
 */
@SpringBootApplication
public class MctranslatorApplication {

    private static final System.Logger LOGGER = System.getLogger(MctranslatorApplication.class.getName());

    /**
     * Arranca la ejecución de la JVM y despliega el contexto ligero de Spring.
     *
     * @param args Parámetros de la terminal.
     */
    public static void main(String[] args) {
        LOGGER.log(System.Logger.Level.INFO, "Iniciando la carga del núcleo de MCTranslator...");
        SpringApplication.run(MctranslatorApplication.class, args);
    }
}
