package com.lucalzt.mctranslator.ports.inbound;

import com.lucalzt.mctranslator.infrastructure.inbound.TranslationConfigDTO;

/**
 * Puerto de Entrada (Primary Port) que define el caso de uso principal del sistema.
 * Permite iniciar el proceso de traducción completo de un modpack a partir de su ruta raíz.
 */
public interface TranslateModpackUseCase {

    /**
     * Ejecuta el pipeline de traducción completo (mods, recursos y configuraciones)
     * para el modpack especificado.
     *
     * @param modpackPath Ruta absoluta o relativa hacia el directorio base del modpack.
     */
    void execute(String modpackPath);

    default void execute(String modpackPath, TranslationConfigDTO overrides) {
        execute(modpackPath);
    }
}
