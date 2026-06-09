package com.lucalzt.mctranslator.infrastructure.outbound.quest;

import com.lucalzt.mctranslator.domain.model.QuestData;
import com.lucalzt.mctranslator.domain.model.QuestSystemType;
import com.lucalzt.mctranslator.domain.service.SnbtSanitizer;
import com.lucalzt.mctranslator.ports.outbound.QuestExtractorPort;
import com.lucalzt.mctranslator.ports.outbound.QuestWriterPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FtbQuestsModernAdapter implements QuestExtractorPort, QuestWriterPort {

    private static final System.Logger LOGGER = System.getLogger(FtbQuestsModernAdapter.class.getName());
    private static final String LANG_DIR = "config/ftbquests/quests/lang";

    private final SnbtSanitizer sanitizer;

    public FtbQuestsModernAdapter(SnbtSanitizer sanitizer) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "SnbtSanitizer no puede ser nulo");
    }

    @Override
    public QuestData extract(Path modpackPath) {
        Objects.requireNonNull(modpackPath, "La ruta del modpack no puede ser nula");

        Path langDir = modpackPath.resolve(LANG_DIR);
        Path enUsFile = langDir.resolve("en_us.snbt");

        if (!Files.isRegularFile(enUsFile)) {
            LOGGER.log(System.Logger.Level.INFO, "No se encontró {0}. Omitiendo extracción.", enUsFile);
            return new QuestData(QuestSystemType.NONE, Map.of(), new byte[0]);
        }

        try {
            String raw = Files.readString(enUsFile);
            String cleaned = sanitizer.sanitize(raw);
            if (!isValidSnbt(cleaned)) {
                LOGGER.log(System.Logger.Level.WARNING, "El contenido de {0} no es SNBT válido", enUsFile);
                return new QuestData(QuestSystemType.NONE, Map.of(), new byte[0]);
            }
            Map<String, String> entries = parseSnbt(cleaned);
            return new QuestData(QuestSystemType.FTB_QUESTS_MODERN, entries, raw.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Error al leer {0}: {1}", enUsFile, e.getMessage());
            return new QuestData(QuestSystemType.NONE, Map.of(), new byte[0]);
        }
    }

    @Override
    public void write(Path modpackPath, QuestData original, Map<String, String> translations) {
        Objects.requireNonNull(modpackPath, "La ruta del modpack no puede ser nula");
        Objects.requireNonNull(original, "Los datos originales no pueden ser nulos");
        Objects.requireNonNull(translations, "El mapa de traducciones no puede ser nulo");

        if (original.systemType() != QuestSystemType.FTB_QUESTS_MODERN) {
            throw new IllegalArgumentException("Este adaptador solo soporta FTB Quests moderno");
        }

        try {
            Path langDir = modpackPath.resolve(LANG_DIR);
            Files.createDirectories(langDir);
            Path esEsFile = langDir.resolve("es_es.snbt");

            String content = generateSnbt(translations);
            Files.writeString(esEsFile, content);

            LOGGER.log(System.Logger.Level.INFO, "Traducciones de quests escritas en {0}", esEsFile);
        } catch (IOException e) {
            throw new RuntimeException("Error al escribir traducciones de quests", e);
        }
    }

    /**
     * Parsea SNBT plano en un mapa clave->valor preservando el orden de inserción.
     * Soporta claves con/sin comillas, valores string, arrays y comas opcionales.
     */
    static Map<String, String> parseSnbt(String snbt) {
        String s = snbt.trim();
        if (s.startsWith("{")) {
            s = s.substring(1);
        }
        if (s.endsWith("}")) {
            s = s.substring(0, s.length() - 1);
        }

        Map<String, String> result = new LinkedHashMap<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == ',') {
                i++;
                continue;
            }
            int[] keyRange = parseKey(s, i);
            if (keyRange == null) break;
            String key = s.substring(keyRange[0], keyRange[1]).trim();
            if (key.startsWith("\"") && key.endsWith("\"")) {
                key = key.substring(1, key.length() - 1);
            }
            i = keyRange[1];
            while (i < s.length() && s.charAt(i) == ' ') i++;
            if (i < s.length() && s.charAt(i) == ':') i++;
            while (i < s.length() && s.charAt(i) == ' ') i++;
            int[] valRange = parseValue(s, i);
            if (valRange == null) break;
            String value = s.substring(valRange[0], valRange[1]).trim();
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(key, value);
            i = valRange[1];
        }
        return result;
    }

    /**
     * Extrae una clave SNBT desde la posición {@code start}.
     * Puede ser quoted ({@code "..."}) o unquoted (caracteres hasta espacio o {@code ':'}).
     */
    private static int[] parseKey(String s, int start) {
        int i = start;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        if (i >= s.length()) return null;
        if (s.charAt(i) == '"') {
            int end = findClosingQuote(s, i);
            if (end == -1) return null;
            return new int[]{i, end + 1};
        }
        int end = i;
        while (end < s.length() && s.charAt(end) != ':' && !Character.isWhitespace(s.charAt(end))) {
            end++;
        }
        if (end == i) return null;
        return new int[]{i, end};
    }

    /**
     * Extrae un valor SNBT desde la posición {@code start}.
     * Maneja strings quoted, arrays con depth tracking de corchetes, o tokens hasta separador.
     */
    private static int[] parseValue(String s, int start) {
        int i = start;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        if (i >= s.length()) return null;
        char c = s.charAt(i);
        if (c == '"') {
            int end = findClosingQuote(s, i);
            if (end == -1) return null;
            return new int[]{i, end + 1};
        }
        if (c == '[') {
            int depth = 0;
            int end = i;
            boolean inStr = false;
            while (end < s.length()) {
                char ch = s.charAt(end);
                if (ch == '"') inStr = !inStr;
                if (!inStr) {
                    if (ch == '[') depth++;
                    else if (ch == ']') {
                        depth--;
                        if (depth == 0) {
                            return new int[]{i, end + 1};
                        }
                    }
                }
                end++;
            }
            return null;
        }
        int end = i;
        while (end < s.length()) {
            char ch = s.charAt(end);
            if (ch == ',' || ch == '}' || ch == '\n' || ch == '\r') {
                break;
            }
            end++;
        }
        if (end == i) return null;
        return new int[]{i, end};
    }

    /**
     * Busca la comilla doble de cierre a partir de {@code start + 1}.
     * Salta secuencias de escape {@code \"} para no confundirlas con el cierre real.
     */
    private static int findClosingQuote(String s, int start) {
        int i = start + 1;
        while (i < s.length()) {
            if (s.charAt(i) == '\\') {
                i += 2;
                continue;
            }
            if (s.charAt(i) == '"') {
                return i;
            }
            i++;
        }
        return -1;
    }

    /**
     * Validación superficial de estructura SNBT.
     * El contenido debe tener formato {@code { ... : ... }} para ser considerado candidate.
     */
    static boolean isValidSnbt(String cleaned) {
        String s = cleaned.trim();
        return s.startsWith("{") && s.endsWith("}") && s.contains(":") && s.length() > 2;
    }

    /**
     * Genera SNBT desde un mapa clave→valor.
     * Claves con puntos van quoted, valores string se escapan, arrays se preservan sin quoting extra.
     */
    static String generateSnbt(Map<String, String> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int count = 0;
        for (Map.Entry<String, String> e : entries.entrySet()) {
            if (count > 0) {
                sb.append(",\n");
            }
            count++;
            String key = e.getKey();
            String val = e.getValue();
            if (key.contains(".") || key.contains(" ")) {
                sb.append("  \"").append(key).append("\": ");
            } else {
                sb.append("  ").append(key).append(": ");
            }
            if (val.startsWith("[") && val.endsWith("]")) {
                sb.append(val);
            } else {
                sb.append(escapeSnbtString(val));
            }
        }
        sb.append("\n}");
        return sb.toString();
    }

    /**
     * Envuelve un string en comillas dobles escapando {@code \"} y {@code \\} internos.
     */
    private static String escapeSnbtString(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
