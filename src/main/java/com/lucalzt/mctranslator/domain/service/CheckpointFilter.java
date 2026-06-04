package com.lucalzt.mctranslator.domain.service;

import com.lucalzt.mctranslator.domain.model.ModLanguageFile;

import java.util.*;

/**
 * Servicio encargado de comparar el archivo de idiomas original contra el estado persistido (checkpoint)
 * para filtrar y descartar las claves que ya han sido traducidas con éxito en ejecuciones anteriores.
 */
public class CheckpointFilter {

    /**
     * Filtra las traducciones de un archivo, descartando las ya procesadas.
     *
     * @param sourceFile     El archivo original del mod.
     * @param translatedKeys El conjunto de identificadores de claves que ya fueron traducidos y guardados.
     * @return Un mapa inmutable que contiene únicamente los pares clave-valor pendientes de procesar.
     */
    public Map<String, String> filter(ModLanguageFile sourceFile, Set<String> translatedKeys) {
        Objects.requireNonNull(sourceFile, "El archivo de idioma original no puede ser nulo");
        Objects.requireNonNull(translatedKeys, "El conjunto de claves procesadas no puede ser nulo");

        Map<String, String> pending = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : sourceFile.translations().entrySet()) {
            if (!translatedKeys.contains(entry.getKey())) {
                pending.put(entry.getKey(), entry.getValue());
            }
        }

        return Collections.unmodifiableMap(pending);
    }
}
