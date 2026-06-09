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

            String modpackPath = readRequired(scanner, "Ruta del modpack: ");

            String engine = readWithDefault(scanner, "Motor (ollama/groq)", defaultEngine);
            while (!"ollama".equalsIgnoreCase(engine) && !"groq".equalsIgnoreCase(engine)) {
                System.out.println("  Motor inválido. Elige 'ollama' o 'groq'.");
                engine = readWithDefault(scanner, "Motor (ollama/groq)", defaultEngine);
            }
            engine = engine.toLowerCase();

            String ollamaUrl = null;
            String ollamaModel = null;
            String groqUrl = null;
            String groqModel = null;
            String groqKeys = null;

            if ("ollama".equals(engine)) {
                String url = readWithDefault(scanner, "URL de Ollama", defaultOllamaUrl);
                if (!url.isBlank() && !url.equals(defaultOllamaUrl)) ollamaUrl = url;
                String model = readWithDefault(scanner, "Modelo Ollama", defaultOllamaModel);
                if (!model.isBlank() && !model.equals(defaultOllamaModel)) ollamaModel = model;
            } else {
                String url = readWithDefault(scanner, "URL de Groq", defaultGroqUrl);
                if (!url.isBlank() && !url.equals(defaultGroqUrl)) groqUrl = url;
                String model = readWithDefault(scanner, "Modelo Groq", defaultGroqModel);
                if (!model.isBlank() && !model.equals(defaultGroqModel)) groqModel = model;
                String keysHint = defaultGroqKey.isBlank() ? "(ninguna)" : "(configurada en application.yml)";
                String keys = readWithDefault(scanner, "API Keys Groq", keysHint);
                if (!keys.isBlank() && !keys.equals(keysHint)) groqKeys = keys;
            }

            int chunkSize = readIntRange(scanner, "Claves por lote (10-150)", defaultChunkSize, 10, 150);

            Boolean questsOnly = null;
            Boolean modsOnly = null;
            String scope = readWithDefault(scanner, "Ámbito (1=mods+quests, 2=solo mods, 3=solo quests)", "1");
            switch (scope) {
                case "2" -> modsOnly = true;
                case "3" -> questsOnly = true;
            }

            return new TranslationConfigDTO(
                    modpackPath, engine, chunkSize,
                    ollamaUrl, ollamaModel, groqUrl, groqModel, groqKeys,
                    null, null, null,
                    questsOnly, modsOnly
            );
        }
    }

    private String readRequired(Scanner scanner, String prompt) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        while (input.isBlank()) {
            System.out.print("  Este campo es obligatorio. " + prompt);
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
                System.out.println("  Valor fuera de rango. Debe estar entre " + min + " y " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("  Debes ingresar un número válido.");
            }
        }
    }
}
