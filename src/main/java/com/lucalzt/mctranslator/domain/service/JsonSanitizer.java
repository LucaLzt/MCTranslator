package com.lucalzt.mctranslator.domain.service;

/**
 * Servicio de dominio puro responsable de limpiar y reparar el formato de las respuestas
 * de texto JSON devueltas por los modelos de lenguaje (LLMs).
 * Remueve decoradores de markdown, caracteres de control no válidos y espacios innecesarios.
 */
public class JsonSanitizer {

    public String sanitize(String rawJson) {
        if (rawJson == null) {
            return "{}";
        }

        String cleaned = rawJson.trim();

        // 1. Remuevo etiquetas de bloques de código Markdown (```json o ```)
        if (cleaned.contains("```json")) {
            int start = cleaned.indexOf("```json") + 7;
            int end = cleaned.indexOf("```", start);
            if (end != -1) {
                cleaned = cleaned.substring(start, end).trim();
            }
        } else if (cleaned.contains("```")) {
            int start = cleaned.indexOf("```") + 3;
            int end = cleaned.indexOf("```", start);
            if (end != -1) {
                cleaned = cleaned.substring(start, end).trim();
            }
        }

        // 2. Limpio los caracteres invisibles y de control ASCII (0 al 31)
        cleaned = cleaned.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        // 3. Por último elimino los caracteres BOM (Byte Order Mark) invisibles que suelen inyectarse
        cleaned = cleaned.replace("\uFEFF", "");

        return cleaned;
    }
}
