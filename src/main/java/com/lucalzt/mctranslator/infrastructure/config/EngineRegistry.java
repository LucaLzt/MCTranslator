package com.lucalzt.mctranslator.infrastructure.config;

import com.lucalzt.mctranslator.ports.outbound.TranslationEnginePort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class EngineRegistry {

    private final Map<String, TranslationEnginePort> engines = new HashMap<>();
    private volatile String activeEngine;

    public void register(String name, TranslationEnginePort adapter) {
        engines.put(name, adapter);
    }

    public void select(String name) {
        if (!engines.containsKey(name)) {
            throw new IllegalArgumentException("Engine not registered: " + name);
        }
        this.activeEngine = name;
    }

    public String getActiveEngineName() {
        return activeEngine;
    }

    public TranslationEnginePort getActive() {
        if (activeEngine == null) return null;
        return engines.get(activeEngine);
    }

    public TranslationEnginePort get(String name) {
        return engines.get(name);
    }
}
