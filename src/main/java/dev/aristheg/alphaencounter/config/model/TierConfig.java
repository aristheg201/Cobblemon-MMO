package dev.aristheg.alphaencounter.config.model;

public final class TierConfig {
    public String id = "regional";
    public float healthMultiplier = 4.0f;
    public String behaviour = "passive";
    public boolean bossBar = true;
    public double bossBarRange = 72.0;
    public String bossBarColor = "YELLOW";

    public void normalize(String fallbackId) {
        if (id == null || id.isBlank()) id = fallbackId;
        healthMultiplier = Math.max(1.0f, healthMultiplier);
        if (behaviour == null || behaviour.isBlank()) behaviour = "passive";
        bossBarRange = Math.max(16.0, bossBarRange);
        if (bossBarColor == null || bossBarColor.isBlank()) bossBarColor = "YELLOW";
    }
}
