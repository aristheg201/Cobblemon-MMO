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
}
