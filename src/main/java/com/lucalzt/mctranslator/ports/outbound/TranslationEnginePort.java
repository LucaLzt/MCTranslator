package com.lucalzt.mctranslator.ports.outbound;

import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;

/**
 * Puerto de Salida (Secondary Port) qye actúa como contrato para la comunicación con los
 * motores de inteligencia artificial (LLMs) encargados de realizar la traducción textual.
 */
public interface TranslationEnginePort {

    /**
     * Envía un lote (chunk) estructurado de traducciones a la API de IA configurada
     * y recupera los textos traducidos y limpios.
     *
     * @param chunk El lote que contiene las claves y valores en el idioma origen.
     * @return El resultado estructurado TranslationResult con las traducciones en español.
     */
    TranslationResult translate(TranslationChunk chunk);
}
