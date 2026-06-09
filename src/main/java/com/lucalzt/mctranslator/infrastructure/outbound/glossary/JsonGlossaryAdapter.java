package com.lucalzt.mctranslator.infrastructure.outbound.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.model.GlossaryEntry;
import com.lucalzt.mctranslator.ports.outbound.GlossaryPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Adaptador de {@link GlossaryPort} con persistencia en archivo JSON.
 * <p>
 * Mantiene un caché en memoria del glosario completo y vuelca (flush) a disco
 * en cada {@link #save(String, String)} para garantizar que no se pierdan
 * entradas si el proceso falla. Al iniciar, carga el archivo {@code .mctranslator/glossary.json}
 * del modpack y lo deja listo en RAM para consultas rápidas.
 * <p>
 * Es la implementación recomendada para producción mientras el glosario tenga
 * menos de ~50.000 entradas. Si escala más allá, migrar a {@code SqliteGlossaryAdapter}
 * sin tocar nada del resto del sistema (ambos implementan {@link GlossaryPort}).
 */
public class JsonGlossaryAdapter implements GlossaryPort {

    private static final System.Logger LOGGER = System.getLogger(JsonGlossaryAdapter.class.getName());
    private static final String GLOSSARY_FILE_NAME = "glossary.json";
    private static final String GLOSSARY_DIR_NAME = ".mctranslator";

    private final ObjectMapper objectMapper;
    private final Path glossaryFile;
    private final Map<String, GlossaryEntry> cache;

    public JsonGlossaryAdapter(ObjectMapper objectMapper, Path modpackPath) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "El ObjectMapper no puede ser nulo");
        Objects.requireNonNull(modpackPath, "La ruta del modpack no puede ser nula");

        this.glossaryFile = modpackPath.resolve(GLOSSARY_DIR_NAME).resolve(GLOSSARY_FILE_NAME);
        this.cache = new HashMap<>();
        loadFromDisk();
    }

    @Override
    public Optional<String> lookup(String termEn) {
        String normalized = normalize(termEn);
        GlossaryEntry entry = cache.get(normalized);
        if (entry == null) {
            return Optional.empty();
        }
        cache.put(normalized, entry.incrementOccurrences());
        return Optional.of(entry.termEs());
    }

    @Override
    public void save(String termEn, String termEs) {
        String normalized = normalize(termEn);
        String normalizedEs = normalize(termEs);

        if (cache.containsKey(normalized)) {
            GlossaryEntry existing = cache.get(normalized);
            cache.put(
                    normalized,
                    new GlossaryEntry(
                            existing.termEn(),
                            existing.termEs(),
                            existing.source(),
                    existing.occurrences() + 1,
                            existing.firstSeen()
            ));
        } else {
            cache.put(
                    normalized,
                    new GlossaryEntry(
                            termEn.trim(),
                            termEs.trim(),
                            "auto",
                            1,
                            Instant.now()
            ));
        }

        flush();
    }

    @Override
    public Map<String, String> findRelevantTerms(Set<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();
        for (String candidate : candidates) {
            String normalized = normalize(candidate);
            GlossaryEntry entry = cache.get(normalized);
            if (entry != null) {
                result.put(entry.termEn(), entry.termEs());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<String, GlossaryEntry> allEntries() {
        return Collections.unmodifiableMap(cache);
    }

    public int size() {
        return cache.size();
    }

    private String normalize(String term) {
        return term.trim().toLowerCase();
    }

    private void loadFromDisk() {
        if (!Files.exists(glossaryFile)) {
            LOGGER.log(System.Logger.Level.DEBUG, "No se encontró glosario previo en {0}. Iniciando vacío.", glossaryFile);
            return;
        }

        try {
            GlossaryData data = objectMapper.readValue(glossaryFile.toFile(), GlossaryData.class);
            if (data != null && data.entries() != null) {
                for (GlossaryEntry entry : data.entries()) {
                    cache.put(normalize(entry.termEn()), entry);
                }
                LOGGER.log(System.Logger.Level.INFO, "Glosario cargado: {0} entradas desde {1}",
                        cache.size(), glossaryFile);
            }
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "No se pudo leer el glosario en {0}. Se iniciará vacío: {1}",
                    glossaryFile, e.getMessage());
        }
    }

    /**
     * Persiste el caché completo en disco sobrescribiendo el archivo JSON.
     * Se llama después de cada {@link #save(String, String)} para asegurar
     * durabilidad ante cortes del proceso. Esto implica reescribir el archivo
     * entero en cada operación, lo que con ~15.000 entradas sigue siendo
     * aceptable. Si el rendimiento empieza a deteriorarse, reemplazar por
     * {@code SqliteGlossaryAdapter}.
     */
    private void flush() {
        try {
            Files.createDirectories(glossaryFile.getParent());
            GlossaryData data = new GlossaryData(cache.values());
            objectMapper.writeValue(glossaryFile.toFile(), data);
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Fallo al escribir el glosario en {0}", glossaryFile, e);
            throw new RuntimeException("Error al persistir el glosario", e);
        }
    }

    private record GlossaryData(Collection<GlossaryEntry> entries) {}
}
