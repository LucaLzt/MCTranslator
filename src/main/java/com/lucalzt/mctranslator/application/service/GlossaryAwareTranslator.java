package com.lucalzt.mctranslator.application.service;

import com.lucalzt.mctranslator.domain.model.TranslationChunk;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.ports.outbound.GlossaryPort;
import com.lucalzt.mctranslator.ports.outbound.TranslationEnginePort;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GlossaryAwareTranslator implements TranslationEnginePort {

    static final String GLOSSARY_CONTEXT_KEY = "__glossary_context__";

    private final TranslationEnginePort delegate;
    private final GlossaryPort glossaryPort;
    private final GlossaryContextBuilder contextBuilder;

    public GlossaryAwareTranslator(
            TranslationEnginePort delegate,
            GlossaryPort glossaryPort,
            GlossaryContextBuilder contextBuilder
    ) {
        this.delegate = delegate;
        this.glossaryPort = glossaryPort;
        this.contextBuilder = contextBuilder;
    }

    @Override
    public TranslationResult translate(TranslationChunk chunk) {
        Set<String> candidates = extractCandidates(chunk.translationsToTranslate());
        Map<String, String> relevantTerms = glossaryPort.findRelevantTerms(candidates);
        String context = contextBuilder.buildContext(relevantTerms);

        TranslationChunk enrichedChunk = enrichChunk(chunk, context);
        TranslationResult result = delegate.translate(enrichedChunk);

        saveNewTerms(chunk.translationsToTranslate(), result.translatedTranslations());
        return result;
    }

    /**
     * Extrae términos candidatos para consultar al glosario a partir de las claves
     * y valores del chunk. Toma palabras que empiezan con mayúscula (nombres propios,
     * nombres de ítems, etc.) porque son las que el LLM tiende a traducir inconsistente.
     */
    static Set<String> extractCandidates(Map<String, String> translations) {
        if (translations == null || translations.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> candidates = new HashSet<>();
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            addCandidateTerms(candidates, entry.getKey());
            addCandidateTerms(candidates, entry.getValue());
        }
        return candidates;
    }

    /**
     * Analiza un texto, lo divide por espacios/guiones/barras y agrega al conjunto
     * las palabras de ≥3 caracteres que empiezan con mayúscula.
     */
    private static void addCandidateTerms(Set<String> candidates, String text) {
        if (text == null || text.isBlank()) return;

        String[] words = text.split("[\\s_/]+");
        for (String word : words) {
            word = word.trim();
            if (word.length() >= 3 && Character.isUpperCase(word.charAt(0))) {
                candidates.add(word);
            }
        }
    }

    /**
     * Si hay contexto de glosario, crea un nuevo chunk con una entrada adicional
     * {@link #GLOSSARY_CONTEXT_KEY} que el adaptador inyectará en el prompt.
     * Si no hay contexto, devuelve el chunk original sin modificaciones.
     */
    private TranslationChunk enrichChunk(TranslationChunk chunk, String context) {
        if (context.isEmpty()) {
            return chunk;
        }

        Map<String, String> enriched = new HashMap<>(chunk.translationsToTranslate());
        enriched.put(GLOSSARY_CONTEXT_KEY, context);
        return new TranslationChunk(chunk.chunkId(), enriched);
    }

    /**
     * Recorre los pares original → traducido y, cuando detecta un valor fuente
     * que contiene un único término con mayúscula (ej.: "Enchanted Blade"), lo
     * persiste en el glosario junto con su traducción. Así el glosario se
     * retroalimenta automáticamente durante la ejecución.
     */
    private void saveNewTerms(Map<String, String> originals, Map<String, String> translated) {
        if (originals == null || translated == null) return;

        for (Map.Entry<String, String> entry : originals.entrySet()) {
            String key = entry.getKey();
            if (GLOSSARY_CONTEXT_KEY.equals(key)) continue;

            String originalValue = entry.getValue();
            String translatedValue = translated.get(key);

            if (originalValue != null && !originalValue.isBlank()
                    && translatedValue != null && !translatedValue.isBlank()) {

                Set<String> originalTerms = extractTermsFromText(originalValue);
                Set<String> translatedTerms = extractTermsFromText(translatedValue);

                if (originalTerms.size() == 1 && translatedTerms.size() == 1) {
                    String enTerm = originalTerms.iterator().next();
                    String esTerm = translatedTerms.iterator().next();
                    glossaryPort.save(enTerm, esTerm);
                }
            }
        }
    }

    private Set<String> extractTermsFromText(String text) {
        Set<String> terms = new HashSet<>();
        String[] words = text.split("[\\s_/]+");
        for (String word : words) {
            word = word.trim();
            if (word.length() >= 3 && Character.isUpperCase(word.charAt(0))) {
                terms.add(word);
            }
        }
        return terms;
    }
}
