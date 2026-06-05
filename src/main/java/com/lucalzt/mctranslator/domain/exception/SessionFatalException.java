package com.lucalzt.mctranslator.domain.exception;

/**
 * Se lanza cuando ocurre un error catastrófico a nivel de sesión completa que impide
 * continuar con el pipeline de traducción (por ejemplo, la pérdida total de credenciales válidas).
 */
public class SessionFatalException extends FatalException {

    /**
     * Construye una nueva excepción fatal de sesión con un mensaje explicativo.
     *
     * @param message Detalle del motivo del aborto del sistema.
     */
    public SessionFatalException(String message) {
        super(String.format("Error Crítico de Sesión: %s. El pipeline completo será abortado.", message));
    }

    /**
     * Construye una nueva excepción fatal de sesión con causa raíz.
     *
     * @param message Detalle del motivo del aborto.
     * @param cause Excepción original de infraestructura.
     */
    public SessionFatalException(String message, Throwable cause) {
        super(String.format("Error Crítico de Sesión: %s. El pipeline completo será abortado.", message), cause);
    }
}
