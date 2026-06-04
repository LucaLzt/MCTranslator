package com.lucalzt.mctranslator.domain.exception;

/**
 * Representa un error no recuperable que debe abortar la ejecución inmediatamente.
 * Se utiliza para fallos de configuración, credenciales inválidas o payloads corruptos.
 */
public abstract class FatalException extends TranslationException {

    /**
     * Construye una excepción fatal con un mensaje detallado.
     *
     * @param message Detalle explicativo del error.
     */
    protected FatalException(String message) {
        super(message);
    }

    /**
     * Construye una excepción fatal con un mensaje y la causa raíz.
     *
     * @param message Detalle explicativo del error.
     * @param cause   Causa original del error.
     */
    protected FatalException(String message, Throwable cause) {
        super(message, cause);
    }
}
