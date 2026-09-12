package dev.aristheg.alphaencounter.config.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageBundle {
    public String language = "en_us";
    public Map<String, MessageProfile> profiles = new LinkedHashMap<>();
    public Map<String, String> text = new LinkedHashMap<>();

    public void normalize(String fallbackLanguage) {
        if (language == null || language.isBlank()) language = fallbackLanguage;
        if (profiles == null) profiles = new LinkedHashMap<>();
        if (text == null) text = new LinkedHashMap<>();

        profiles.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
        for (Map.Entry<String, MessageProfile> entry : profiles.entrySet()) {
            entry.getValue().normalize(entry.getKey());
        }
        text.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
    }
}
