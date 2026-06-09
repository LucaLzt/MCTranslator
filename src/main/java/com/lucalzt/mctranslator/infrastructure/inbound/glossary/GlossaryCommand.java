package com.lucalzt.mctranslator.infrastructure.inbound.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lucalzt.mctranslator.domain.model.GlossaryEntry;
import com.lucalzt.mctranslator.infrastructure.outbound.glossary.JsonGlossaryAdapter;
import com.lucalzt.mctranslator.ports.outbound.GlossaryPort;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
        name = "glossary",
        description = "Gestiona el glosario de traducciones del modpack.",
        subcommands = {
                GlossaryCommand.ListCommand.class,
                GlossaryCommand.EditCommand.class,
                GlossaryCommand.ExportCommand.class
        }
)
public class GlossaryCommand implements Callable<Integer> {

    private static final System.Logger LOGGER = System.getLogger(GlossaryCommand.class.getName());

    @Option(
            names = "--modpack",
            description = "Ruta del modpack (usa el directorio actual si no se especifica).",
            required = false,
            paramLabel = "<RUTA>"
    )
    private Path modpackPath;

    @Option(
            names = { "-h", "--help" },
            description = "Muestra la ayuda del comando glossary.",
            usageHelp = true,
            required = false
    )
    private boolean help;

    @Override
    public Integer call() {
        if (help) {
            return 0;
        }
        LOGGER.log(System.Logger.Level.WARNING,
                "Uso: mctranslator glossary [list|edit|export] --modpack <RUTA>");
        return 0;
    }

    GlossaryPort createAdapter() {
        Path path = modpackPath != null ? modpackPath : Path.of(".");
        Path absolute = path.toAbsolutePath().normalize();
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        return new JsonGlossaryAdapter(mapper, absolute);
    }

    @Command(
            name = "list",
            description = "Lista las entradas del glosario."
    )
    static class ListCommand implements Callable<Integer> {

        @ParentCommand
        private GlossaryCommand parent;

        @Option(
                names = "--filter",
                description = "Filtra términos que contengan el texto (case-insensitive).",
                required = false,
                paramLabel = "<TEXTO>"
        )
        private String filter;

        @Override
        public Integer call() {
            GlossaryPort glossary = parent.createAdapter();
            List<GlossaryEntry> entries = glossary.findAll();

            if (filter != null && !filter.isBlank()) {
                String lower = filter.trim().toLowerCase();
                entries = entries.stream()
                        .filter(e -> e.termEn().toLowerCase().contains(lower)
                                || e.termEs().toLowerCase().contains(lower))
                        .collect(Collectors.toList());
            }

            if (entries.isEmpty()) {
                System.out.println("(glosario vacío)");
                return 0;
            }

            System.out.printf("%-4s %-30s %-30s %s%n", "#", "Término (EN)", "Traducción (ES)", "Veces");
            System.out.println("-".repeat(80));
            int i = 1;
            for (GlossaryEntry entry : entries) {
                System.out.printf("%-4d %-30s %-30s %d%n",
                        i++,
                        truncate(entry.termEn(), 28),
                        truncate(entry.termEs(), 28),
                        entry.occurrences());
            }
            System.out.println("-".repeat(80));
            System.out.printf("Total: %d entradas%n", entries.size());
            return 0;
        }

        private String truncate(String s, int max) {
            return s.length() <= max ? s : s.substring(0, max - 3) + "...";
        }
    }

    @Command(
            name = "edit",
            description = "Agrega o modifica una entrada del glosario."
    )
    static class EditCommand implements Callable<Integer> {

        @ParentCommand
        private GlossaryCommand parent;

        @Parameters(
                index = "0",
                description = "Término en inglés a agregar o modificar.",
                paramLabel = "<TERM_EN>"
        )
        private String termEn;

        @Option(
                names = "--translation",
                description = "Traducción al español.",
                required = true,
                paramLabel = "<TRADUCCION>"
        )
        private String translation;

        @Override
        public Integer call() {
            if (termEn == null || termEn.isBlank()) {
                System.err.println("Error: el término en inglés no puede estar vacío.");
                return 1;
            }
            if (translation == null || translation.isBlank()) {
                System.err.println("Error: la traducción no puede estar vacía.");
                return 1;
            }

            GlossaryPort glossary = parent.createAdapter();
            glossary.save(termEn.trim(), translation.trim());

            System.out.printf("Entrada actualizada: \"%s\" → \"%s\"%n",
                    termEn.trim(), translation.trim());
            return 0;
        }
    }

    @Command(
            name = "export",
            description = "Exporta el glosario a un archivo JSON o CSV."
    )
    static class ExportCommand implements Callable<Integer> {

        @ParentCommand
        private GlossaryCommand parent;

        @Option(
                names = "--output",
                description = "Ruta del archivo de salida.",
                required = false,
                defaultValue = "glossary-export.json",
                paramLabel = "<ARCHIVO>"
        )
        private Path output;

        @Option(
                names = "--format",
                description = "Formato de exportación: json (por defecto) o csv.",
                required = false,
                defaultValue = "json",
                paramLabel = "<FORMATO>"
        )
        private String format;

        @Override
        public Integer call() {
            GlossaryPort glossary = parent.createAdapter();
            List<GlossaryEntry> entries = glossary.findAll();

            if ("csv".equalsIgnoreCase(format)) {
                return exportCsv(entries);
            }
            return exportJson(entries);
        }

        private int exportJson(List<GlossaryEntry> entries) {
            try {
                ObjectMapper mapper = JsonMapper.builder()
                        .addModule(new JavaTimeModule())
                        .build();
                byte[] bytes = mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(entries);
                Files.write(output, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.printf("Glosario exportado a %s (%d entradas)%n",
                        output.toAbsolutePath().normalize(), entries.size());
                return 0;
            } catch (IOException e) {
                System.err.println("Error al exportar glosario: " + e.getMessage());
                return 1;
            }
        }

        private int exportCsv(List<GlossaryEntry> entries) {
            try {
                String csv = entries.stream()
                        .map(e -> String.format("\"%s\",\"%s\",\"%s\",%d,%s",
                                escapeCsv(e.termEn()),
                                escapeCsv(e.termEs()),
                                escapeCsv(e.source()),
                                e.occurrences(),
                                e.firstSeen().toString()))
                        .collect(Collectors.joining(System.lineSeparator(),
                                "term_en,term_es,source,occurrences,first_seen" + System.lineSeparator(),
                                ""));
                Files.writeString(output, csv, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.printf("Glosario exportado a %s (%d entradas)%n",
                        output.toAbsolutePath().normalize(), entries.size());
                return 0;
            } catch (IOException e) {
                System.err.println("Error al exportar glosario: " + e.getMessage());
                return 1;
            }
        }

        private String escapeCsv(String value) {
            return value.replace("\"", "\"\"");
        }
    }
}
