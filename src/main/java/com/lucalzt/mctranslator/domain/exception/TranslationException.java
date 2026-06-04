package com.lucalzt.mctranslator.domain.exception;

/**
 * Excepción base abstracta para todos los errores del dominio de traducción.
 * Actúa como la raíz común de la jerarquía de excepciones.
 */
public abstract class TranslationException extends RuntimeException {

    /**
     * Construye una nueva excepción de traducción con un mensaje específico.
     *
     * @param message Detalle explicativo del error ocurrido.
     */
    protected TranslationException(String message) {
        super(message);
    }

    /**
     * Construye una nueva excepción de traducción con un mensaje y una causa raíz.
     *
     * @param message Detalle explicativo del error ocurrido.
     * @param cause   La causa original del error.
     */
    protected TranslationException(String message, Throwable cause) {
        super(message, cause);
    }
}
