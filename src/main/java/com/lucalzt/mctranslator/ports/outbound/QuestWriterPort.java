package com.lucalzt.mctranslator.ports.outbound;

import com.lucalzt.mctranslator.domain.model.QuestData;

import java.nio.file.Path;
import java.util.Map;

/**
 * Puerto de salida para la escritura de traducciones en sistemas de misiones (quests).
 * <p>
 * Cada implementación conoce el formato específico de un sistema y escribe el archivo
 * traducido en la ubicación y formato nativo correspondiente (ej.: {@code es_es.snbt}
 * para FTB Quests moderno, o reemplazo inline en capítulos legacy).
 */
public interface QuestWriterPort {

    /**
     * Escribe las traducciones en el sistema de misiones detectado.
     * <p>
     * Para formatos modernos (lang file) genera un archivo nuevo. Para formatos legacy
     * (inline) modifica solo los campos traducibles preservando el resto de la estructura.
     *
     * @param modpackPath  Ruta raíz del modpack donde escribir.
     * @param original     {@link QuestData} original extraído (necesario para conocer
     *                     el sistema y el payload raw en formatos legacy).
     * @param translations Mapa clave → traducción al español generada por el motor de IA.
     */
    void write(Path modpackPath, QuestData original, Map<String, String> translations);
}
