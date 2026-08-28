package vn.svframe.svframemmo.cobblemon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import vn.svframe.svframemmo.cobblemon.fusion.FusionTier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class IntegrationConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("SVFrameMMOCobblemon/config.json");

    public PotaraConfig potara = new PotaraConfig();
    public FusionConfig fusion = new FusionConfig();
    public VfxConfig vfx = new VfxConfig();

    public static final class PotaraConfig {
        @SerializedName("potara-earrings") public PotaraItem basic = new PotaraItem("minecraft:amethyst_shard", 71001);
        @SerializedName("potara-earrings-level-2") public PotaraItem level2 = new PotaraItem("minecraft:amethyst_shard", 71002);
        @SerializedName("potara-earrings-of-advancement") public PotaraItem advancement = new PotaraItem("minecraft:amethyst_shard", 71003);
        @SerializedName("potara-earrings-of-god") public PotaraItem god = new PotaraItem("minecraft:amethyst_shard", 71004);

        public Map<FusionTier, PotaraItem> byTier() {
            LinkedHashMap<FusionTier, PotaraItem> out = new LinkedHashMap<>();
            out.put(FusionTier.BASIC, basic);
            out.put(FusionTier.LEVEL_2, level2);
            out.put(FusionTier.ADVANCEMENT, advancement);
            out.put(FusionTier.GOD, god);
            return Map.copyOf(out);
        }
    }

    public static final class FusionConfig {
        @SerializedName("potara-action-cooldown-seconds") public int potaraActionCooldownSeconds = 10;
        @SerializedName("dance-duration-seconds") public int danceDurationSeconds = 10 * 60;
        @SerializedName("dance-cooldown-seconds") public int danceCooldownSeconds = 15 * 60;
        @SerializedName("stat-conversion") public StatConversion statConversion = new StatConversion();
    }

    public static final class VfxConfig {
        @SerializedName("potara-fusion-effects") public java.util.List<String> potaraFusionEffects = java.util.List.of("mega_showdown:kyurem_black", "mega_showdown:kyurem_white");
        @SerializedName("move-broadcast-radius") public double moveBroadcastRadius = 32d;
        @SerializedName("full-quality-distance") public double fullQualityDistance = 18d;
        @SerializedName("max-viewers-per-emission") public int maxViewersPerEmission = 48;
        @SerializedName("max-snowstorm-packets-per-tick") public int maxSnowstormPacketsPerTick = 256;
        @SerializedName("max-fallback-particles-per-emission") public int maxFallbackParticlesPerEmission = 32;
    }

    /**
     * Conversion from native Pokemon stats into flat SVFrame RPG stats before the fusion rank multiplier.
     * Values are deliberately configurable because Pokemon and Minecraft stat scales are different.
     */
    public static final class StatConversion {
        @SerializedName("hp-to-max-health") public double hpToMaxHealth = 0.10d;
        @SerializedName("special-attack-to-max-mana") public double specialAttackToMaxMana = 0.10d;
        @SerializedName("speed-to-max-stamina") public double speedToMaxStamina = 0.10d;
        @SerializedName("offense-to-attack-damage") public double offenseToAttackDamage = 0.02d;
    }

    public static final class PotaraItem {
        public String item;
        @SerializedName("custom-model-data") public int customModelData;
        public PotaraItem() { }
        public PotaraItem(String item, int customModelData) { this.item = item; this.customModelData = customModelData; }
        public Identifier itemId() {
            Identifier parsed = Identifier.tryParse(item == null ? "" : item.trim());
            if (parsed == null) throw new IllegalArgumentException("Invalid Potara vanilla item id: " + item);
            return parsed;
        }
    }

    public static IntegrationConfig load() throws IOException {
        Files.createDirectories(FILE.getParent());
        if (!Files.exists(FILE)) {
            IntegrationConfig created = new IntegrationConfig();
            created.validate();
            Files.writeString(FILE, GSON.toJson(created));
            return created;
        }
        IntegrationConfig parsed = GSON.fromJson(Files.readString(FILE), IntegrationConfig.class);
        if (parsed == null) parsed = new IntegrationConfig();
        parsed.validate();
        return parsed;
    }

    private void validate() {
        if (potara == null) potara = new PotaraConfig();
        if (fusion == null) fusion = new FusionConfig();
        if (fusion.statConversion == null) fusion.statConversion = new StatConversion();
        if (vfx == null) vfx = new VfxConfig();
        if (fusion.potaraActionCooldownSeconds != 10) throw new IllegalArgumentException("potara-action-cooldown-seconds is fixed at 10");
        if (fusion.danceDurationSeconds != 10 * 60) throw new IllegalArgumentException("dance-duration-seconds is fixed at 600");
        if (fusion.danceCooldownSeconds != 15 * 60) throw new IllegalArgumentException("dance-cooldown-seconds is fixed at 900");
        validateScale("hp-to-max-health", fusion.statConversion.hpToMaxHealth);
        validateScale("special-attack-to-max-mana", fusion.statConversion.specialAttackToMaxMana);
        validateScale("speed-to-max-stamina", fusion.statConversion.speedToMaxStamina);
        validateScale("offense-to-attack-damage", fusion.statConversion.offenseToAttackDamage);
        if (vfx.potaraFusionEffects == null || vfx.potaraFusionEffects.isEmpty()) throw new IllegalArgumentException("potara-fusion-effects must contain at least one Mega Showdown effect");
        for (String rawEffect : vfx.potaraFusionEffects) {
            Identifier potaraEffect = Identifier.tryParse(rawEffect == null ? "" : rawEffect.trim());
            if (potaraEffect == null || !"mega_showdown".equals(potaraEffect.getNamespace())) throw new IllegalArgumentException("potara-fusion-effects must contain only mega_showdown effect ids");
        }
        if (!Double.isFinite(vfx.moveBroadcastRadius) || vfx.moveBroadcastRadius <= 0d || vfx.moveBroadcastRadius > 64d) throw new IllegalArgumentException("move-broadcast-radius must be 0..64");
        if (!Double.isFinite(vfx.fullQualityDistance) || vfx.fullQualityDistance < 0d || vfx.fullQualityDistance > vfx.moveBroadcastRadius) throw new IllegalArgumentException("full-quality-distance must be within move-broadcast-radius");
        if (vfx.maxViewersPerEmission < 1 || vfx.maxViewersPerEmission > 128) throw new IllegalArgumentException("max-viewers-per-emission must be 1..128");
        if (vfx.maxSnowstormPacketsPerTick < 1 || vfx.maxSnowstormPacketsPerTick > 4096) throw new IllegalArgumentException("max-snowstorm-packets-per-tick must be 1..4096");
        if (vfx.maxFallbackParticlesPerEmission < 1 || vfx.maxFallbackParticlesPerEmission > 256) throw new IllegalArgumentException("max-fallback-particles-per-emission must be 1..256");

        Set<String> pairs = new HashSet<>();
        for (Map.Entry<FusionTier, PotaraItem> entry : potara.byTier().entrySet()) {
            PotaraItem spec = entry.getValue();
            if (spec == null) throw new IllegalArgumentException("Missing Potara config for " + entry.getKey());
            Identifier itemId = spec.itemId();
            if (!"minecraft".equals(itemId.getNamespace())) throw new IllegalArgumentException("Potara item must be a vanilla Minecraft item for " + entry.getKey() + ": " + itemId);
            if (!Registries.ITEM.containsId(itemId)) throw new IllegalArgumentException("Unknown vanilla item for " + entry.getKey() + ": " + itemId);
            if (spec.customModelData < 0) throw new IllegalArgumentException("custom-model-data must be >= 0 for " + entry.getKey());
            String pair = itemId + "#" + spec.customModelData;
            if (!pairs.add(pair)) throw new IllegalArgumentException("Duplicate Potara vanilla item/CMD pair: " + pair);
        }
    }

    private static void validateScale(String name, double value) {
        if (!Double.isFinite(value) || value < 0d) throw new IllegalArgumentException(name + " must be finite and >= 0");
    }
}
