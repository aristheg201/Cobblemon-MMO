package dev.aristheg.alphaencounter.config.model;

public final class BossBarProfile {
    public String id = "regional";
    public boolean enabled = true;
    public String title = "";
    public double range = 72.0;
    public String color = "YELLOW";
    public String style = "PROGRESS";

    public void normalize(String fallbackId) {
        if (id == null || id.isBlank()) id = fallbackId;
        if (title == null) title = "";
        range = Math.max(16.0, range);
        if (color == null || color.isBlank()) color = "YELLOW";
        if (style == null || style.isBlank()) style = "PROGRESS";
    }
}
