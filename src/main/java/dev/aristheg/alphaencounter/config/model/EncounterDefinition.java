package dev.aristheg.alphaencounter.config.model;

import java.util.ArrayList;
import java.util.List;

public final class EncounterDefinition {
    public String id = "unnamed";
    public String displayName = "Unnamed Alpha";
    public boolean enabled = true;
    public String tier = "regional";
    public String pokemon = "pikachu level=50 alpha=true";
    public String messageProfile = "";
    public String bossBarProfile = "";
    public Spawn spawn = new Spawn();
    public Animations animations = new Animations();
    public boolean catchable = false;
    public int catchPhaseSeconds = 0;
    public float catchHealthPercent = 0.10f;
    public boolean rewardOnAdminDefeat = false;
    public List<String> rewardCommands = new ArrayList<>();
    public transient String categoryId = "misc";

    public void normalize(String fallbackId) {
        if (id == null || id.isBlank()) id = fallbackId;
        if (displayName == null || displayName.isBlank()) displayName = id;
        if (tier == null || tier.isBlank()) tier = "regional";
        if (pokemon == null) pokemon = "";
        if (messageProfile == null || messageProfile.isBlank()) messageProfile = tier;
        if (bossBarProfile == null || bossBarProfile.isBlank()) bossBarProfile = tier;
        if (spawn == null) spawn = new Spawn();
        spawn.normalize();
        if (animations == null) animations = new Animations();
        animations.normalize();
        catchPhaseSeconds = Math.max(0, catchPhaseSeconds);
        catchHealthPercent = Math.max(0.01f, Math.min(1.0f, catchHealthPercent));
        if (rewardCommands == null) rewardCommands = new ArrayList<>();
    }

    public static final class Spawn {
        public List<String> dimensions = new ArrayList<>();
        public List<String> biomes = new ArrayList<>();
        public double weight = 1.0;
        public int minDistance = 24;
        public int maxDistance = 64;
        public int maxActive = 1;
        public long globalCooldownTicks = 12000;

        public void normalize() {
            if (dimensions == null) dimensions = new ArrayList<>();
            if (biomes == null) biomes = new ArrayList<>();
            weight = Math.max(0.0, weight);
            minDistance = Math.max(8, minDistance);
            maxDistance = Math.max(minDistance, maxDistance);
            maxActive = Math.max(1, maxActive);
            globalCooldownTicks = Math.max(0, globalCooldownTicks);
        }
    }

    public static final class Animations {
        public String aggro = "cry";
        public String hit = "";
        public String battleStart = "";
        public String battleEnd = "";
        public String defeat = "";

        public void normalize() {
            if (aggro == null) aggro = "";
            if (hit == null) hit = "";
            if (battleStart == null) battleStart = "";
            if (battleEnd == null) battleEnd = "";
            if (defeat == null) defeat = "";
        }
    }
}
