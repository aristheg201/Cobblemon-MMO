package dev.aristheg.alphaencounter.config.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageBundle {
    public String language = "en_us";
    public Map<String, MessageProfile> profiles = new LinkedHashMap<>();

    public void normalize(String fallbackLanguage) {
        if (language == null || language.isBlank()) language = fallbackLanguage;
        if (profiles == null) profiles = new LinkedHashMap<>();
        profiles.entrySet().removeIf(e -> e.getValue() == null);
        for (Map.Entry<String, MessageProfile> entry : profiles.entrySet()) entry.getValue().normalize(entry.getKey());
    }
}
