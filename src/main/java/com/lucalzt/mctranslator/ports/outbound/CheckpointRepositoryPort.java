package com.lucalzt.mctranslator.ports.outbound;

import java.util.Set;

/**
 * Puerto de Salida (Secondary Port) que define el contrato de persistencia para el progreso parcial.
 * Evitar volver a traducir claves que ya fueron procesadas y guardadas exitosamente en el Resource Pack.
 */
public interface CheckpointRepositoryPort {

    /**
     * Guarda de forma persistente el conjunto de claves que ya han sido traducidas con éxito para un mod.
     *
     * @param modId          Identificador único del mod.
     * @param translatedKeys El conjunto con todos los identificadores de localización ya traducidos.
     */
    void save(String modId, Set<String> translatedKeys);

    /**
     * Recupera el conjunto de claves ya traducidas para un mod desde el almacenamiento persistente.
     *
     * @param modId Identificador único del mod.
     * @return Un conjunto con las claves ya procesadas. Retorna un conjunto vacío si no hay progreso previo.
     */
    Set<String> load(String modId);
}
