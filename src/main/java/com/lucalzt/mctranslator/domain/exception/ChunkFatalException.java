package com.lucalzt.mctranslator.domain.exception;

/**
 * Se lanza cuando un lote específico falla de forma catastrófica e irrecuperable
 * (por ejemplo, contenido ofensivo detectado por filtros de seguridad o sintaxis insalvable).
 */
public class ChunkFatalException extends FatalException {

    private final int chunkId;

    /**
     * Construye la excepción para un lote de traducción específico.
     *
     * @param message Detalles del error fatal.
     * @param chunkId Identificador del lote de traducción.
     */
    public ChunkFatalException(String message, int chunkId) {
        super(String.format("El Lote ID [%d] falló fatalmente. Abortando este lote. Detalles: %s", chunkId, message));
        this.chunkId = chunkId;
    }

    /**
     * Construye la excepción para un lote con causa raíz asociada.
     *
     * @param message Detalles del error fatal.
     * @param chunkId Identificador del lote de traducción.
     * @param cause   Causa raíz de la falla.
     */
    public ChunkFatalException(String message, int chunkId, Throwable cause) {
        super(String.format("El Lote ID [%d] falló fatalmente. Abortando este lote. Detalles: %s", chunkId, message), cause);
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
