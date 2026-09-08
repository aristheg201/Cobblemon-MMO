package dev.aristheg.alphaencounter.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.config.model.BehaviourConfig;
import dev.aristheg.alphaencounter.config.model.CategoryConfig;
import dev.aristheg.alphaencounter.config.model.EncounterDefinition;
import dev.aristheg.alphaencounter.config.model.GeneralConfig;
import dev.aristheg.alphaencounter.config.model.TierConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class AlphaEncounterConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path root = FabricLoader.getInstance().getConfigDir().resolve("alpha-encounter");
    private final Map<String, TierConfig> tiers = new LinkedHashMap<>();
    private final Map<String, BehaviourConfig> behaviours = new LinkedHashMap<>();
    private final Map<String, CategoryConfig> categories = new LinkedHashMap<>();
    private final Map<String, EncounterDefinition> encounters = new LinkedHashMap<>();
    private final Map<String, List<EncounterDefinition>> spawnIndex = new HashMap<>();
    private final Map<String, List<EncounterDefinition>> candidateCache = new HashMap<>();
    private GeneralConfig general = new GeneralConfig();

    public synchronized void load() {
        try {
            Files.createDirectories(root);
            migrateLegacyLayout();
            ensureDefaultTree();
            loadGlobal();
            loadBehaviours();
            loadTiers();
            loadCategoriesAndEncounters();
            buildSpawnIndex();
            AlphaEncounterMod.LOGGER.info("Loaded Alpha-Encounter config: {} tiers, {} behaviours, {} categories, {} encounters.", tiers.size(), behaviours.size(), categories.size(), encounters.size());
        } catch (Exception e) {
            AlphaEncounterMod.LOGGER.error("Failed to load Alpha-Encounter config tree.", e);
        }
    }

    public Path root() {
        return root;
    }

    public Path stateFile() {
        return root.resolve("persistent/state.json");
    }

    public GeneralConfig general() {
        return general;
    }

    public TierConfig tier(String id) {
        TierConfig tier = tiers.get(id);
        if (tier != null) return tier;
        return tiers.getOrDefault("regional", defaultRegionalTier());
    }

    public BehaviourConfig behaviourForTier(String tierId) {
        TierConfig tier = tier(tierId);
        BehaviourConfig behaviour = behaviours.get(tier.behaviour);
        if (behaviour != null) return behaviour;
        return behaviours.getOrDefault("passive", defaultPassiveBehaviour());
    }

    public EncounterDefinition encounter(String id) {
        return encounters.get(id);
    }

    public List<String> encounterIds() {
        return encounters.keySet().stream().sorted().toList();
    }

    public List<EncounterDefinition> candidates(String dimension, String biome) {
        String cacheKey = key(dimension, biome);
        return candidateCache.computeIfAbsent(cacheKey, ignored -> {
            Set<EncounterDefinition> result = new LinkedHashSet<>();
            addIndexed(result, key(dimension, biome));
            addIndexed(result, key(dimension, "*"));
            addIndexed(result, key("*", biome));
            addIndexed(result, key("*", "*"));
            return List.copyOf(result);
        });
    }

    private void addIndexed(Set<EncounterDefinition> target, String key) {
        List<EncounterDefinition> found = spawnIndex.get(key);
        if (found != null) target.addAll(found);
    }

    private void loadGlobal() throws IOException {
        general = read(root.resolve("config.json"), GeneralConfig.class, new GeneralConfig());
        general.normalize();
    }

    private void loadBehaviours() throws IOException {
        behaviours.clear();
        Path dir = root.resolve("behaviours");
        for (Path file : jsonFiles(dir)) {
            BehaviourConfig value = read(file, BehaviourConfig.class, null);
            if (value == null) continue;
            String id = stripJson(file.getFileName().toString());
            value.normalize(id);
            behaviours.put(value.id, value);
        }
    }

    private void loadTiers() throws IOException {
        tiers.clear();
        Path dir = root.resolve("tiers");
        for (Path file : jsonFiles(dir)) {
            TierConfig value = read(file, TierConfig.class, null);
            if (value == null) continue;
            String id = stripJson(file.getFileName().toString());
            value.normalize(id);
            tiers.put(value.id, value);
        }
    }

    private void loadCategoriesAndEncounters() throws IOException {
        categories.clear();
        encounters.clear();
        Path dir = root.resolve("categories");
        if (Files.notExists(dir)) return;

        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> folders = stream.filter(Files::isDirectory).sorted().toList();
            for (Path folder : folders) {
                String folderId = folder.getFileName().toString();
                CategoryConfig category = read(folder.resolve("settings.json"), CategoryConfig.class, new CategoryConfig());
                category.normalize(folderId);
                categories.put(category.id, category);
                if (!category.enabled) continue;

                Path encounterDir = folder.resolve("encounters");
                for (Path file : jsonFiles(encounterDir)) {
                    EncounterDefinition def = read(file, EncounterDefinition.class, null);
                    if (def == null) continue;
                    def.normalize(stripJson(file.getFileName().toString()));
                    def.categoryId = category.id;
                    if (def.spawn.dimensions.isEmpty()) def.spawn.dimensions.addAll(category.dimensions);
                    if (def.spawn.biomes.isEmpty()) def.spawn.biomes.addAll(category.biomes);
                    if (def.pokemon.isBlank()) {
                        AlphaEncounterMod.LOGGER.error("Encounter '{}' has an empty pokemon property string; skipping.", def.id);
                        continue;
                    }
                    if (!tiers.containsKey(def.tier)) {
                        AlphaEncounterMod.LOGGER.error("Encounter '{}' references missing tier '{}'; skipping.", def.id, def.tier);
                        continue;
                    }
                    EncounterDefinition previous = encounters.put(def.id, def);
                    if (previous != null) AlphaEncounterMod.LOGGER.warn("Duplicate encounter id '{}'; last file wins.", def.id);
                }
            }
        }
    }

    private void buildSpawnIndex() {
        spawnIndex.clear();
        candidateCache.clear();
        for (EncounterDefinition def : encounters.values()) {
            if (!def.enabled || def.spawn.weight <= 0.0) continue;
            List<String> dimensions = def.spawn.dimensions.isEmpty() ? List.of("*") : def.spawn.dimensions;
            List<String> biomes = def.spawn.biomes.isEmpty() ? List.of("*") : def.spawn.biomes;
            for (String dimension : dimensions) {
                for (String biome : biomes) {
                    spawnIndex.computeIfAbsent(key(dimension, biome), ignored -> new ArrayList<>()).add(def);
                }
            }
        }
    }

    private String key(String dimension, String biome) {
        return dimension + "|" + biome;
    }

    private void migrateLegacyLayout() throws IOException {
        Path legacyConfig = root.resolve("config.json");
        if (Files.exists(legacyConfig)) {
            JsonElement parsed;
            try {
                parsed = JsonParser.parseString(Files.readString(legacyConfig));
            } catch (Exception e) {
                parsed = null;
            }
            if (parsed != null && parsed.isJsonObject()) {
                JsonObject object = parsed.getAsJsonObject();
                boolean legacy = object.has("general") || object.has("tiers") || object.has("encounters");
                if (legacy) {
                    Path backup = root.resolve("legacy/config-v1.json");
                    Files.createDirectories(backup.getParent());
                    if (Files.notExists(backup)) Files.copy(legacyConfig, backup);

                    JsonObject global = object.has("general") && object.get("general").isJsonObject() ? object.getAsJsonObject("general") : new JsonObject();
                    writeJson(legacyConfig, global);

                    if (object.has("tiers") && object.get("tiers").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("tiers").entrySet()) {
                            if (entry.getValue().isJsonObject()) migrateLegacyTier(entry.getKey(), entry.getValue().getAsJsonObject());
                        }
                    }
                    if (object.has("encounters") && object.get("encounters").isJsonArray()) {
                        JsonArray list = object.getAsJsonArray("encounters");
                        for (JsonElement element : list) if (element.isJsonObject()) migrateLegacyEncounter(element.getAsJsonObject());
                    }
                    AlphaEncounterMod.LOGGER.info("Migrated legacy monolithic Alpha-Encounter config into folder layout. Backup: {}", backup);
                }
            }
        }

        Path oldState = root.resolve("state.json");
        Path newState = stateFile();
        if (Files.exists(oldState) && Files.notExists(newState)) {
            Files.createDirectories(root.resolve("legacy"));
            Files.createDirectories(newState.getParent());
            Files.copy(oldState, root.resolve("legacy/state-v1.json"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(oldState, newState, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void migrateLegacyTier(String id, JsonObject old) throws IOException {
        BehaviourConfig behaviour = new BehaviourConfig();
        behaviour.id = id;
        behaviour.aggressive = bool(old, "aggressive", !"regional".equals(id));
        behaviour.aggroRadius = number(old, "aggroRadius", 32.0);
        behaviour.leashRadius = number(old, "leashRadius", 64.0);
        behaviour.chaseSpeed = number(old, "chaseSpeed", 1.0);
        behaviour.fieldAttackRange = number(old, "battleTriggerDistance", 3.0);
        behaviour.fieldAttackDamage = defaultFieldDamage(id);
        behaviour.fieldAttackCooldownTicks = "apex".equals(id) ? 20 : ("signature".equals(id) ? 22 : 24);
        behaviour.fieldAttackKnockback = "apex".equals(id) ? 0.55 : 0.35;
        behaviour.fieldAttackAnimation = "physical";
        behaviour.reengageCooldownTicks = integer(old, "reengageCooldownTicks", 60);
        behaviour.normalize(id);

        TierConfig tier = new TierConfig();
        tier.id = id;
        tier.healthMultiplier = (float) number(old, "healthMultiplier", 4.0);
        tier.behaviour = id;
        tier.bossBar = bool(old, "bossBar", true);
        tier.bossBarRange = number(old, "bossBarRange", 72.0);
        tier.bossBarColor = string(old, "bossBarColor", "YELLOW");
        tier.normalize(id);

        Path behaviourFile = root.resolve("behaviours").resolve(safe(id) + ".json");
        Path tierFile = root.resolve("tiers").resolve(safe(id) + ".json");
        if (Files.notExists(behaviourFile)) writeJson(behaviourFile, behaviour);
        if (Files.notExists(tierFile)) writeJson(tierFile, tier);
    }

    private void migrateLegacyEncounter(JsonObject encounter) throws IOException {
        String id = string(encounter, "id", "legacy_unnamed");
        String categoryId = deriveCategory(encounter);
        Path categoryDir = root.resolve("categories").resolve(categoryId);
        Path settingsFile = categoryDir.resolve("settings.json");
        if (Files.notExists(settingsFile)) {
            CategoryConfig category = new CategoryConfig();
            category.id = categoryId;
            category.displayName = categoryId.replace('_', ' ');
            writeJson(settingsFile, category);
        }
        Path encounterFile = categoryDir.resolve("encounters").resolve(safe(id) + ".json");
        if (Files.notExists(encounterFile)) writeJson(encounterFile, encounter);
    }

    private String deriveCategory(JsonObject encounter) {
        try {
            JsonObject spawn = encounter.getAsJsonObject("spawn");
            JsonArray biomes = spawn.getAsJsonArray("biomes");
            if (biomes != null && !biomes.isEmpty()) return safe(biomes.get(0).getAsString());
        } catch (Exception ignored) {
        }
        return "misc";
    }

    private void ensureDefaultTree() throws IOException {
        Files.createDirectories(root.resolve("tiers"));
        Files.createDirectories(root.resolve("behaviours"));
        Files.createDirectories(root.resolve("categories"));
        Files.createDirectories(root.resolve("persistent"));
        Files.createDirectories(root.resolve("legacy"));

        writeIfMissing(root.resolve("config.json"), new GeneralConfig());
        writeIfMissing(root.resolve("behaviours/passive.json"), defaultPassiveBehaviour());
        writeIfMissing(root.resolve("behaviours/aggressive.json"), defaultAggressiveBehaviour());
        writeIfMissing(root.resolve("behaviours/apex_hunter.json"), defaultApexBehaviour());
        writeIfMissing(root.resolve("tiers/regional.json"), defaultRegionalTier());
        writeIfMissing(root.resolve("tiers/signature.json"), defaultSignatureTier());
        writeIfMissing(root.resolve("tiers/apex.json"), defaultApexTier());

        Path yeager = root.resolve("categories/bestiary_mount_yeager");
        CategoryConfig category = new CategoryConfig();
        category.id = "bestiary_mount_yeager";
        category.displayName = "Mount Yeager";
        category.dimensions.add("minecraft:overworld");
        category.biomes.add("bestiary:mount_yeager");
        writeIfMissing(yeager.resolve("settings.json"), category);

        EncounterDefinition godzilla = new EncounterDefinition();
        godzilla.id = "alpha_bestiary_mount_yeager_tyranitar_godzilla";
        godzilla.displayName = "Godzilla Tyranitar";
        godzilla.tier = "apex";
        godzilla.pokemon = "tyranitar level=100 alpha=true cosmetic_item-godzilla";
        godzilla.spawn.weight = 0.08;
        godzilla.spawn.minDistance = 36;
        godzilla.spawn.maxDistance = 88;
        godzilla.spawn.maxActive = 1;
        godzilla.spawn.globalCooldownTicks = 72000;
        godzilla.catchable = true;
        godzilla.catchPhaseSeconds = 120;
        godzilla.catchHealthPercent = 0.10f;
        godzilla.spawnMessage = "[Alpha] {name} has emerged in Mount Yeager.";
        godzilla.defeatMessage = "[Alpha] {name} has been defeated.";
        godzilla.catchMessage = "[Alpha] {name} is vulnerable to capture for a short time.";
        writeIfMissing(yeager.resolve("encounters/tyranitar_godzilla.json"), godzilla);
    }

    private BehaviourConfig defaultPassiveBehaviour() {
        BehaviourConfig b = new BehaviourConfig();
        b.id = "passive";
        b.aggressive = false;
        b.aggroRadius = 32;
        b.leashRadius = 64;
        b.chaseSpeed = 1.0;
        b.fieldAttackRange = 3.0;
        b.fieldAttackDamage = 6.0;
        b.fieldAttackCooldownTicks = 24;
        b.fieldAttackKnockback = 0.25;
        b.fieldAttackAnimation = "physical";
        b.reengageCooldownTicks = 60;
        return b;
    }

    private BehaviourConfig defaultAggressiveBehaviour() {
        BehaviourConfig b = new BehaviourConfig();
        b.id = "aggressive";
        b.aggressive = true;
        b.aggroRadius = 40;
        b.leashRadius = 80;
        b.chaseSpeed = 1.1;
        b.fieldAttackRange = 3.0;
        b.fieldAttackDamage = 9.0;
        b.fieldAttackCooldownTicks = 22;
        b.fieldAttackKnockback = 0.35;
        b.fieldAttackAnimation = "physical";
        b.reengageCooldownTicks = 50;
        return b;
    }

    private BehaviourConfig defaultApexBehaviour() {
        BehaviourConfig b = new BehaviourConfig();
        b.id = "apex_hunter";
        b.aggressive = true;
        b.aggroRadius = 56;
        b.leashRadius = 112;
        b.chaseSpeed = 1.25;
        b.fieldAttackRange = 3.5;
        b.fieldAttackDamage = 14.0;
        b.fieldAttackCooldownTicks = 20;
        b.fieldAttackKnockback = 0.55;
        b.fieldAttackAnimation = "physical";
        b.reengageCooldownTicks = 40;
        return b;
    }

    private TierConfig defaultRegionalTier() {
        TierConfig t = new TierConfig();
        t.id = "regional";
        t.healthMultiplier = 4.0f;
        t.behaviour = "passive";
        t.bossBarRange = 72;
        t.bossBarColor = "YELLOW";
        return t;
    }

    private TierConfig defaultSignatureTier() {
        TierConfig t = new TierConfig();
        t.id = "signature";
        t.healthMultiplier = 7.0f;
        t.behaviour = "aggressive";
        t.bossBarRange = 88;
        t.bossBarColor = "BLUE";
        return t;
    }

    private TierConfig defaultApexTier() {
        TierConfig t = new TierConfig();
        t.id = "apex";
        t.healthMultiplier = 12.0f;
        t.behaviour = "apex_hunter";
        t.bossBarRange = 112;
        t.bossBarColor = "PURPLE";
        return t;
    }

    private double defaultFieldDamage(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "apex" -> 14.0;
            case "signature" -> 9.0;
            default -> 6.0;
        };
    }

    private <T> T read(Path file, Class<T> type, T fallback) {
        if (Files.notExists(file)) return fallback;
        try {
            T value = GSON.fromJson(Files.readString(file), type);
            return value == null ? fallback : value;
        } catch (Exception e) {
            AlphaEncounterMod.LOGGER.error("Could not read config file {}.", file, e);
            return fallback;
        }
    }

    private List<Path> jsonFiles(Path dir) throws IOException {
        if (Files.notExists(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        }
    }

    private void writeIfMissing(Path file, Object value) throws IOException {
        if (Files.notExists(file)) writeJson(file, value);
    }

    private void writeJson(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, value instanceof JsonElement element ? GSON.toJson(element) : GSON.toJson(value));
    }

    private String stripJson(String name) {
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    private String safe(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }

    private boolean bool(JsonObject object, String key, boolean fallback) {
        try { return object.has(key) ? object.get(key).getAsBoolean() : fallback; } catch (Exception e) { return fallback; }
    }

    private int integer(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; } catch (Exception e) { return fallback; }
    }

    private double number(JsonObject object, String key, double fallback) {
        try { return object.has(key) ? object.get(key).getAsDouble() : fallback; } catch (Exception e) { return fallback; }
    }

    private String string(JsonObject object, String key, String fallback) {
        try { return object.has(key) ? object.get(key).getAsString() : fallback; } catch (Exception e) { return fallback; }
    }
}
