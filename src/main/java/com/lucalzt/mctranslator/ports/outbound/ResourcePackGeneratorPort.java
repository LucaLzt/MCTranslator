package com.lucalzt.mctranslator.ports.outbound;

import com.lucalzt.mctranslator.domain.model.TranslationResult;

import java.nio.file.Path;

/**
 * Puerto de Salida (Secondary Pack) responsable de materializar las traducciones de mods
 * en disco estructurando el Resource Pack final que consumirá el cliente de Minecraft.
 */
public interface ResourcePackGeneratorPort {

    /**
     * Escribe las traducciones validadas dentro de la jerarquía de directorios del Resource Pack.
     * Genera la estructura de assets y el archivo de idioma es_es.json correspondiente.
     *
     * @param modId             Identificador único del mod.
     * @param result            Resultado estructurado con las traducciones finales al español.
     * @param resourcePacksPath Ruta física base donde se inyectará el Resource Pack.
     */
    void generate(String modId, TranslationResult result, Path resourcePacksPath);
}
