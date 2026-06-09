package com.lucalzt.mctranslator.ports.outbound;

import com.lucalzt.mctranslator.domain.model.TranslationResult;

import java.nio.file.Path;
import java.util.Set;

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

    /**
     * Verifica si el Resource Pack ya contiene un archivo de traducción completo para un mod,
     * comparando las claves existentes en es_es.json contra las claves originales del en_us.json.
     * <p>
     * Sirve como entrada temprana para evitar re-procesar mods cuyas traducciones ya fueron
     * generadas en ejecuciones anteriores, incluso si los checkpoints se perdieron.
     *
     * @param modId             Identificador único del mod.
     * @param originalKeys      Conjunto de claves originales del archivo en_us.json del mod.
     * @param resourcePacksPath Ruta física base del Resource Pack.
     * @return true si el es_es.json existe y contiene al menos todas las claves originales.
     */
    default boolean hasCompleteTranslation(String modId, Set<String> originalKeys, Path resourcePacksPath) {
        return false;
    }
}
