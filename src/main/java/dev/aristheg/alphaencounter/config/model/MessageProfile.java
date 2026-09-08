package dev.aristheg.alphaencounter.config.model;

public final class MessageProfile {
    public String id = "default";
    public String spawn = "";
    public String aggro = "";
    public String battleStart = "";
    public String battleEnd = "";
    public String defeat = "";
    public String catchAvailable = "";
    public String despawn = "";

    public void normalize(String fallbackId) {
        if (id == null || id.isBlank()) id = fallbackId;
        if (spawn == null) spawn = "";
        if (aggro == null) aggro = "";
        if (battleStart == null) battleStart = "";
        if (battleEnd == null) battleEnd = "";
        if (defeat == null) defeat = "";
        if (catchAvailable == null) catchAvailable = "";
        if (despawn == null) despawn = "";
    }
}
