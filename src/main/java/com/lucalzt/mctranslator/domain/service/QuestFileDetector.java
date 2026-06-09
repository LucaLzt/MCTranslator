package com.lucalzt.mctranslator.domain.service;

import com.lucalzt.mctranslator.domain.model.QuestSystemType;

import java.nio.file.Path;

/**
 * Servicio de dominio encargado de detectar qué sistema de misiones (quests)
 * está presente en un modpack de Minecraft.
 * <p>
 * La detección sigue un orden de precedencia: FTB Quests moderno (1.21+) >
 * FTB Quests legacy (pre-1.21) > BetterQuesting > NONE.
 * <p>
 * A diferencia de los puertos ({@code QuestExtractorPort}, {@code QuestWriterPort}),
 * esta interfaz vive en {@code domain/service} porque la lógica de detección es una
 * regla de negocio pura (orden de precedencia, qué archivos revisar), no un límite
 * arquitectónico del hexágono. Sin embargo, se define como interfaz porque su
 * implementación concreta requiere acceso al sistema de archivos, que pertenece a
 * la capa de infraestructura.
 */
public interface QuestFileDetector {
    QuestSystemType detect(Path modpackPath);
}
