package com.lucalzt.mctranslator.ports.outbound;

import com.lucalzt.mctranslator.domain.model.ModLanguageFile;

import java.nio.file.Path;

/**
 * Puerto de Salida (Secondary Port) responsable de interactuar con el sistema de archivos
 * para extraer las claves de idioma originales desde el interior de los archivos empaquetados de los mods.
 */
public interface ModExtractorPort {

    /**
     * Abre un archivo empaquetado de mod (.jar), localiza el archivo de traducción base (en_us.json)
     * y extrae su información estructurada.
     *
     * @param jarPath Ruta física hacia el archivo .jar del mod.
     * @return Un objeto de dominio ModLanguageFile con el mapa de traducciones origen.
     */
    ModLanguageFile extract(Path jarPath);
}
