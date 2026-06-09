package com.lucalzt.mctranslator.domain.service;

public class SnbtSanitizer {

    public String sanitize(String rawSnbt) {
        if (rawSnbt == null) {
            return "{}";
        }

        String cleaned = rawSnbt.trim();

        if (cleaned.contains("```snbt")) {
            int start = cleaned.indexOf("```snbt") + 7;
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

        cleaned = cleaned.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        cleaned = cleaned.replace("\uFEFF", "");

        return cleaned;
    }
}
