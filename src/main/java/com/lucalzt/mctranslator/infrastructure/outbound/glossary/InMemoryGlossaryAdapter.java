package com.lucalzt.mctranslator.infrastructure.outbound.glossary;

import com.lucalzt.mctranslator.ports.outbound.GlossaryPort;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adaptador de {@link GlossaryPort} exclusivamente en memoria, sin persistencia.
 * <p>
 * Los términos se almacenan en un {@link HashMap} y se pierden al finalizar
 * la JVM. Diseñado para pruebas unitarias y entornos donde no se necesita
 * mantener el glosario entre ejecuciones.
 * <p>
 * Si se necesita persistencia, usar {@link JsonGlossaryAdapter} en su lugar.
 * Ambas implementaciones son intercambiables porque comparten el mismo puerto
 * {@link GlossaryPort}.
 */
public class InMemoryGlossaryAdapter implements GlossaryPort {

    private final Map<String, String> store = new HashMap<>();

    @Override
    public Optional<String> lookup(String termEn) {
        return Optional.ofNullable(store.get(normalize(termEn)));
    }

    @Override
    public void save(String termEn, String termEs) {
        store.put(normalize(termEn), termEs.trim());
    }

    @Override
    public Map<String, String> findRelevantTerms(Set<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();
        for (String candidate : candidates) {
            String normalized = normalize(candidate);
            String translation = store.get(normalized);
            if (translation != null) {
                result.put(candidate.trim(), translation);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<String, String> allEntries() {
        return Collections.unmodifiableMap(store);
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }

    private String normalize(String term) {
        return term.trim().toLowerCase();
    }
}
