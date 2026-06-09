package com.lucalzt.mctranslator.application.service;

import java.util.Map;

/**
 * Construye un bloque de texto con las traducciones aprobadas del glosario
 * para inyectar al inicio del prompt del LLM. Esto fuerza al modelo a usar
 * las traducciones ya registradas en vez de inventar variantes nuevas,
 * garantizando consistencia entre lotes y archivos.
 */
public class GlossaryContextBuilder {

    private static final String HEADER = "GLOSARIO APROBADO (usa estas traducciones exactas, sin variaciones):";

    public String buildContext(Map<String, String> relevantTerms) {
        if (relevantTerms == null || relevantTerms.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append(System.lineSeparator());
        for (Map.Entry<String, String> entry : relevantTerms.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(" → ").append(entry.getValue()).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
