package com.lucalzt.mctranslator.infrastructure.inbound;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Scanner;

@Component
public class InteractiveWizard {

    @Value("${mctranslator.engine:ollama}")
    private String defaultEngine;

    @Value("${mctranslator.chunk-size:50}")
    private int defaultChunkSize;

    @Value("${mctranslator.ollama.url:http://localhost:11434}")
    private String defaultOllamaUrl;

    @Value("${mctranslator.ollama.model:mc-test}")
    private String defaultOllamaModel;

    @Value("${mctranslator.groq.url:https://api.groq.com/openai/v1}")
    private String defaultGroqUrl;

    @Value("${mctranslator.groq.model:meta-llama/llama-4-scout-17b-16e-instruct}")
    private String defaultGroqModel;

    @Value("${mctranslator.groq.key:}")
    private String defaultGroqKey;

    public TranslationConfigDTO promptUser() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println();
            System.out.println("============================================");
            System.out.println("   MCTranslator - Asistente interactivo");
            System.out.println("============================================");
            System.out.println();

            String modpackPath = readRequired(scanner, "\uD83D\uDCC1 Ruta del modpack: ");

            String engine = readWithDefault(scanner, "\uD83E\uDD16 Motor (ollama/groq)", defaultEngine);
            while (!"ollama".equalsIgnoreCase(engine) && !"groq".equalsIgnoreCase(engine)) {
                System.out.println("  \u274C Motor inv\u00e1lido. Elige 'ollama' o 'groq'.");
                engine = readWithDefault(scanner, "\uD83E\uDD16 Motor (ollama/groq)", defaultEngine);
            }
            engine = engine.toLowerCase();

            String ollamaUrl = null;
            String ollamaModel = null;
            String groqUrl = null;
            String groqModel = null;
            String groqKeys = null;

            if ("ollama".equals(engine)) {
                String url = readWithDefault(scanner, "\uD83C\uDF10 URL de Ollama", defaultOllamaUrl);
                if (!url.isBlank() && !url.equals(defaultOllamaUrl)) ollamaUrl = url;
                String model = readWithDefault(scanner, "\uD83D\uDCE6 Modelo Ollama", defaultOllamaModel);
                if (!model.isBlank() && !model.equals(defaultOllamaModel)) ollamaModel = model;
            } else {
                String url = readWithDefault(scanner, "\uD83C\uDF10 URL de Groq", defaultGroqUrl);
                if (!url.isBlank() && !url.equals(defaultGroqUrl)) groqUrl = url;
                String model = readWithDefault(scanner, "\uD83D\uDCE6 Modelo Groq", defaultGroqModel);
                if (!model.isBlank() && !model.equals(defaultGroqModel)) groqModel = model;
                String keysHint = defaultGroqKey.isBlank() ? "(ninguna)" : "(configurada en application.yml)";
                String keys = readWithDefault(scanner, "\uD83D\uDD11 API Keys Groq", keysHint);
                if (!keys.isBlank() && !keys.equals(keysHint)) groqKeys = keys;
            }

            int chunkSize = readIntRange(scanner, "\uD83D\uDCCA Claves por lote (10-150)", defaultChunkSize, 10, 150);

            return new TranslationConfigDTO(
                    modpackPath, engine, chunkSize,
                    ollamaUrl, ollamaModel, groqUrl, groqModel, groqKeys,
                    null, null, null
            );
        }
    }

    private String readRequired(Scanner scanner, String prompt) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        while (input.isBlank()) {
            System.out.print("  \u26A0\uFE0F Este campo es obligatorio. " + prompt);
            input = scanner.nextLine().trim();
        }
        return input;
    }

    private String readWithDefault(Scanner scanner, String prompt, String defaultValue) {
        System.out.print(prompt + " [" + defaultValue + "]: ");
        String input = scanner.nextLine().trim();
        return input.isBlank() ? defaultValue : input;
    }

    private int readIntRange(Scanner scanner, String prompt, int defaultValue, int min, int max) {
        while (true) {
            try {
                System.out.print(prompt + " [" + defaultValue + "]: ");
                String input = scanner.nextLine().trim();
                if (input.isBlank()) return defaultValue;
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) return value;
                System.out.println("  \u26A0\uFE0F Valor fuera de rango. Debe estar entre " + min + " y " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("  \u274C Debes ingresar un n\u00famero v\u00e1lido.");
            }
        }
    }
}
