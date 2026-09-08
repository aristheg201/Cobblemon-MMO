package dev.aristheg.alphaencounter.config.model;

import java.util.ArrayList;
import java.util.List;

public final class CategoryConfig {
    public String id = "misc";
    public String displayName = "Misc";
    public boolean enabled = true;
    public List<String> dimensions = new ArrayList<>();
    public List<String> biomes = new ArrayList<>();

    public void normalize(String fallbackId) {
        if (id == null || id.isBlank()) id = fallbackId;
        if (displayName == null || displayName.isBlank()) displayName = id;
        if (dimensions == null) dimensions = new ArrayList<>();
        if (biomes == null) biomes = new ArrayList<>();
    }
}
