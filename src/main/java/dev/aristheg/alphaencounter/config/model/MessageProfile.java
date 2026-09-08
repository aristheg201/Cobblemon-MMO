package dev.aristheg.alphaencounter.config.model;

public final class MessageProfile {
    public String id = "default";
    public String spawn = "<gradient:#ff3b30:#ffb000><bold>⚠ ALPHA ENCOUNTER</bold></gradient> <white><ae_name></white> <gray>has appeared.</gray>";
    public String defeat = "<gold><bold><ae_name></bold></gold> <yellow>was defeated by <ae_player>.</yellow>";
    public String captureLocked = "<red><ae_name> must be defeated in battle before it can be captured.</red>";
    public String catchStart = "<green><bold>CATCH PHASE</bold></green> <white><ae_name></white> <gray>is vulnerable.</gray> <yellow>Required Ball: <ae_required_ball></yellow>";
    public String catchWrongBall = "<red>Only <ae_required_ball> can capture <ae_name> during the catch phase.</red>";
    public String catchExpired = "<gray>The catch window for <ae_name> has expired.</gray>";
    public String caught = "<aqua><bold><ae_player></bold></aqua> <gray>caught</gray> <white><ae_name></white><gray>!</gray>";
    public String bossBar = "<red><bold><ae_name></bold></red> <dark_gray>•</dark_gray> <white><ae_hp_percent>%</white>";

    public void normalize(String fallbackId) {
        if (id == null || id.isBlank()) id = fallbackId;
        if (spawn == null) spawn = "";
        if (defeat == null) defeat = "";
        if (captureLocked == null) captureLocked = "";
        if (catchStart == null) catchStart = "";
        if (catchWrongBall == null) catchWrongBall = "";
        if (catchExpired == null) catchExpired = "";
        if (caught == null) caught = "";
        if (bossBar == null) bossBar = "";
    }
}
