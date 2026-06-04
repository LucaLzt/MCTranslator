package com.lucalzt.mctranslator.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * Representa un archivo de localización extraído de un Mod de Minecraft.
 * Contiene el mapa de traducciones original leído típicamente de un en_us.json.
 */
public record ModLanguageFile(
        String modId,
        String sourceLanguage,
        Map<String, String> translations
) {

    /**
     * Constructor que valida el estado del archivo de localización
     * y asegura la inmutabilidad profunda de las traducciones cargadas.
     */
    public ModLanguageFile {
        Objects.requireNonNull(modId, "El identificador del mod no puede ser nulo");
        Objects.requireNonNull(sourceLanguage, "El idioma de origen no puede ser nulo");
        Objects.requireNonNull(translations, "El mapa de traducciones no puede ser nulo");

        if (modId.isBlank()) {
            throw new IllegalArgumentException("El identificador del mod no puede estar vacío");
        }
        if (sourceLanguage.isBlank()) {
            throw new IllegalArgumentException("El idioma de origen no puede estar vacío");
        }

        translations = Map.copyOf(translations);
    }
}
