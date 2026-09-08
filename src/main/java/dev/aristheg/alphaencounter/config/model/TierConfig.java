package dev.aristheg.alphaencounter.config.model;

public final class TierConfig {
    public String id = "regional";
    public float healthMultiplier = 4.0f;
    public String behaviour = "passive";
    public String bossBarProfile = "";

    public void normalize(String fallbackId) {
        if (id == null || id.isBlank()) id = fallbackId;
        healthMultiplier = Math.max(1.0f, healthMultiplier);
        if (behaviour == null || behaviour.isBlank()) behaviour = "passive";
        if (bossBarProfile == null || bossBarProfile.isBlank()) bossBarProfile = id;
    }
}
