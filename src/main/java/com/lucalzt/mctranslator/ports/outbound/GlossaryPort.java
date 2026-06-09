package com.lucalzt.mctranslator.ports.outbound;

import com.lucalzt.mctranslator.domain.model.GlossaryEntry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Puerto de Salida (Secondary Port) que define el contrato para un glosario persistente
 * de términos traducidos. Su objetivo es garantizar consistencia en las traducciones
 * entre distintos lotes, archivos y mods dentro de un mismo modpack, evitando que
 * un mismo término en inglés sea traducido de forma distinta por el LLM.
 */
public interface GlossaryPort {

    /**
     * Busca la traducción al español de un término en inglés.
     *
     * @param termEn Término original en inglés.
     * @return Un {@code Optional} con la traducción si existe, o vacío si el término
     *         aún no ha sido registrado.
     */
    Optional<String> lookup(String termEn);

    /**
     * Guarda o actualiza la traducción de un término en inglés. Si el término ya
     * existe, se incrementa su contador de ocurrencias; si no, se crea una nueva
     * entrada.
     *
     * @param termEn Término original en inglés.
     * @param termEs Traducción al español.
     */
    void save(String termEn, String termEs);

    /**
     * Dado un conjunto de términos candidatos, devuelve un mapa con aquellos que
     * ya existen en el glosario junto con su traducción. Útil para filtrar solo
     * los términos relevantes antes de inyectar contexto al prompt del LLM.
     *
     * @param candidates Conjunto de términos en inglés a consultar.
     * @return Mapa con los términos que ya tienen traducción registrada
     *         (término → traducción). Nunca es {@code null}.
     */
    Map<String, String> findRelevantTerms(Set<String> candidates);

    /**
     * Devuelve todas las entradas del glosario ordenadas alfabéticamente por
     * término en inglés (case-insensitive). Útil para el comando CLI {@code list}
     * y para exportaciones completas del glosario.
     *
     * @return Lista inmutable de todas las entradas, nunca {@code null}.
     */
    List<GlossaryEntry> findAll();
}
