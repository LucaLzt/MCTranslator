package com.lucalzt.mctranslator.domain.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Value Object que encapsula la semántica y estructura física de directorios de un Modpack de Minecraft.
 * Actúa como la única fuente de verdad para la resolución de rutas dentro del sistema.
 */
public record ModpackPathResolver(Path modpackPath) {

    /**
     * Constructor compacto para validar que la ruta raíz provista sea válida y no nula.
     */
    public ModpackPathResolver {
        Objects.requireNonNull(modpackPath, "La ruta base del modpack no puede ser nula.");
    }

    /**
     * Obtiene el directorio donde se ubican los archivos modificadores del juego (.jar).
     *
     * @return El path absoluto o relativo al directorio de mods.
     */
    public Path getModsPath() {
        return modpackPath.resolve("mods");
    }

    /**
     * Obtiene la ruta del directorio de localización de FTB Quests (1.21+).
     *
     * @return El path del directorio donde residen los archivos .snbt de idioma de misiones.
     */
    public Path getFtbQuestsLangPath() {
        return modpackPath.resolve("config/ftbquests/quests/lang");
    }

    /**
     * Obtiene la raíz del directorio destinado a alojar el Resource Pack generado en español.
     *
     * @return El path hacia el Resource Pack exclusivo de MCTranslator.
     */
    public Path getResourcePacksPath() {
        return modpackPath.resolve("resourcepakcs/MCTranslator-ES");
    }
}
