package com.lucalzt.mctranslator.ports.outbound;

import com.lucalzt.mctranslator.domain.model.QuestData;

import java.nio.file.Path;

/**
 * Puerto de salida para la extracción de textos traducibles desde sistemas de misiones (quests).
 * <p>
 * Cada implementación conoce el formato específico de un sistema (FTB Quests SNBT,
 * BetterQuesting JSON, etc.) y devuelve un {@link QuestData} normalizado con el
 * contenido a traducir.
 */
public interface QuestExtractorPort {

    /**
     * Extrae los textos traducibles del sistema de misiones presente en el modpack.
     *
     * @param modpackPath Ruta raíz del modpack a inspeccionar.
     * @return {@link QuestData} con las entradas extraídas. Si no se detecta ningún
     *         sistema de misiones conocido, devuelve {@code QuestData(NONE, Map.of(), new byte[0])}.
     */
    QuestData extract(Path modpackPath);
}
