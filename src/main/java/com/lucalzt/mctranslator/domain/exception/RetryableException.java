package com.lucalzt.mctranslator.domain.exception;

/**
 * Representa un error que potencialmente puede ser resuelto reintentando la operación.
 * Útil para fallos de red temporales, límites de tasa o respuestas de IA incompletas.
 */
public abstract class RetryableException extends TranslationException {

    /**
     * Construye una excepción reintentable con un mensaje detallado.
     *
     * @param message Detalle explicativo del error.
     */
    protected RetryableException(String message) {
        super(message);
    }

    /**
     * Construye una excepción reintentable con un mensaje y la causa del fallo.
     *
     * @param message Detalle explicativo del error.
     * @param cause   Causa original del error.
     */
    protected RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
