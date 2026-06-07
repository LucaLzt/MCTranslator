package com.lucalzt.mctranslator.infrastructure.inbound;

public record TranslationConfigDTO(
    String modpackPath,
    String engine,
    Integer chunkSize,
    String ollamaUrl,
    String ollamaModel,
    String groqUrl,
    String groqModel,
    String groqKeys
) {}
