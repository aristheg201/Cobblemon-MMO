package dev.aristheg.alphaencounter.config.model;

public final class GeneralConfig {
    public String language = "en_us";
    public boolean usePlaceholderApi = true;
    public int spawnCheckIntervalTicks = 100;
    public double spawnAttemptChance = 0.08;
    public int huntUpdateIntervalTicks = 10;
    public int bossBarUpdateIntervalTicks = 5;
    public int stateSaveIntervalTicks = 1200;
    public boolean loadEncounterChunksOnRestore = true;
    public int missingEntityGraceTicks = 200;
    public int battlePendingTimeoutTicks = 60;

    public void normalize() {
        if (language == null || language.isBlank()) language = "en_us";
        language = language.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        spawnCheckIntervalTicks = Math.max(20, spawnCheckIntervalTicks);
        spawnAttemptChance = Math.max(0.0, Math.min(1.0, spawnAttemptChance));
        huntUpdateIntervalTicks = Math.max(1, huntUpdateIntervalTicks);
        bossBarUpdateIntervalTicks = Math.max(1, bossBarUpdateIntervalTicks);
        stateSaveIntervalTicks = Math.max(200, stateSaveIntervalTicks);
        missingEntityGraceTicks = Math.max(40, missingEntityGraceTicks);
        battlePendingTimeoutTicks = Math.max(20, battlePendingTimeoutTicks);
    }
}
