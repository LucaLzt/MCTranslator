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
     * Obtiene el directorio de capítulos de FTB Quests en formato legacy (pre-1.21).
     * <p>
     * En versiones anteriores a 1.21, el texto de las misiones está embebido inline
     * dentro de los archivos {@code *.snbt} de esta carpeta, no en un archivo de idioma separado.
     *
     * @return El path del directorio donde residen los archivos {@code .snbt} de capítulos.
     */
    public Path getFtbQuestsChaptersPath() {
        return modpackPath.resolve("config/ftbquests/quests/chapters");
    }

    /**
     * Obtiene el directorio de configuración de BetterQuesting.
     * <p>
     * BetterQuesting almacena sus misiones en {@code QuestDatabase.json} dentro de este directorio.
     *
     * @return El path del directorio de configuración de BetterQuesting.
     */
    public Path getBetterQuestingPath() {
        return modpackPath.resolve("config/betterquesting");
    }

    /**
     * Obtiene la raíz del directorio destinado a alojar el Resource Pack generado en español.
     *
     * @return El path hacia el Resource Pack exclusivo de MCTranslator.
     */
    public Path getResourcePacksPath() {
        return modpackPath.resolve("resourcepacks/MCTranslator-ES");
    }
}
