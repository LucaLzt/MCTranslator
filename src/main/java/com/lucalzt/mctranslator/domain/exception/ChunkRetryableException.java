package com.lucalzt.mctranslator.domain.exception;

/**
 * Se lanza cuando un lote (chunk) de traducción específico falla de una manera que es elegible para reintento
 * (por ejemplo, claves faltantes, JSON truncado o límites de tasa en la petición).
 */
public class ChunkRetryableException extends RetryableException {

    private final int chunkId;

    /**
     * Construye la excepción asociando el lote que falló.
     *
     * @param message Detalles del error.
     * @param chunkId Identificador del lote de traducción.
     */
    public ChunkRetryableException(String message, int chunkId) {
        super(String.format("El Lote ID [%d] falló pero es elegible para reintento. Detalles: %s", chunkId, message));
        this.chunkId = chunkId;
    }

    /**
     * Construye la excepción asociando el lote y la causa del fallo.
     *
     * @param message Detalles del error.
     * @param chunkId Identificador del lote de traducción.
     * @param cause   Causa original de la falla.
     */
    public ChunkRetryableException(String message, int chunkId, Throwable cause) {
        super(String.format("El Lote ID [%d] falló pero es elegible para reintento. Detalles: %s", chunkId, message), cause);
        this.chunkId = chunkId;
    }

    /**
     * Obtiene el identificador del lote afectado.
     *
     * @return El identificador numérico del lote.
     */
    public int getChunkId() {
        return chunkId;
    }
}
