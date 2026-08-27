package vn.svframe.svframemmo.manager;

import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.trigger.NativeTriggerRegistry;
import vn.svframe.svframemmo.trigger.Trigger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Native implementation of SVFrameMMO experience tables and claim persistence. */
public final class ExperienceTableManager {
    public record ExperienceTable(String id, List<ExperienceItem> items) { }
    public record ExperienceItem(String id, int period, int firstTrigger, int lastTrigger, int fixedLevel,
                                 double claimChance, double failReduction, List<Trigger> triggers) {
        public boolean roll(int reachedLevel, int timesCollected) {
            if (fixedLevel > -1) return fixedLevel == reachedLevel;
            if (reachedLevel > lastTrigger) return false;
            if (period == 0 && timesCollected > 0) return false;
            int claimsRequired = reachedLevel + 1 - (firstTrigger + timesCollected * period);
            if (claimsRequired < 1) return false;
            double chance = 1d - (1d - claimChance) * Math.pow(failReduction, claimsRequired);
            return ThreadLocalRandom.current().nextDouble() < chance;
        }
    }

    private final Map<String, ExperienceTable> tables = new LinkedHashMap<>();

    public void reload(Path directory) throws IOException {
        tables.clear();
        if (!Files.isDirectory(directory)) throw new IOException("Missing exp-table directory: " + directory);
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".yml")).sorted().toList()) {
                Map<String, Object> root = YamlLite.map(YamlLite.parse(path));
                for (Map.Entry<String, Object> tableEntry : root.entrySet()) {
                    if (!(tableEntry.getValue() instanceof Map<?, ?> tableRaw))
                        throw new IOException("Experience table '" + tableEntry.getKey() + "' is not a section in " + path);
                    register(parseTable(tableEntry.getKey(), stringMap(tableRaw)));
                }
            }
        }
    }

    public void register(ExperienceTable table) {
        String key = normalize(table.id());
        if (tables.putIfAbsent(key, table) != null) throw new IllegalStateException("Duplicate experience table '" + table.id() + "'");
    }

    public ExperienceTable get(String id) { return id == null ? null : tables.get(normalize(id)); }
    public ExperienceTable getOrThrow(String id) {
        ExperienceTable found = get(id);
        if (found == null) throw new IllegalArgumentException("Could not find experience table '" + id + "'");
        return found;
    }
    public Collection<ExperienceTable> getAll() { return List.copyOf(tables.values()); }
    public int size() { return tables.size(); }

    public void claim(String tableId, String objectKey, PlayerData player, int reachedLevel) {
        if (tableId == null || tableId.isBlank()) return;
        claim(getOrThrow(tableId), objectKey, player, reachedLevel);
    }

    public void claim(ExperienceTable table, String objectKey, PlayerData player, int reachedLevel) {
        if (table == null) return;
        for (ExperienceItem item : table.items()) {
            String claimKey = claimKey(objectKey, item.id());
            int count = player.getClaimCount(claimKey);
            if (!item.roll(reachedLevel, count)) continue;
            player.setClaimCount(claimKey, count + 1);
            for (Trigger trigger : item.triggers()) trigger.schedule(player);
        }
    }

    public void unclaim(String tableId, String objectKey, PlayerData player, boolean reset) {
        if (tableId == null || tableId.isBlank()) return;
        unclaim(getOrThrow(tableId), objectKey, player, reset);
    }

    public void unclaim(ExperienceTable table, String objectKey, PlayerData player, boolean reset) {
        if (table == null) return;
        for (ExperienceItem item : table.items()) {
            String claimKey = claimKey(objectKey, item.id());
            int count = player.getClaimCount(claimKey);
            for (int i = 0; i < count; i++) for (Trigger trigger : item.triggers()) if (trigger.removable()) trigger.remove(player);
            if (reset) player.setClaimCount(claimKey, 0);
        }
    }

    public void applyTemporary(String tableId, String objectKey, PlayerData player) {
        if (tableId == null || tableId.isBlank()) return;
        applyTemporary(getOrThrow(tableId), objectKey, player);
    }

    public void applyTemporary(ExperienceTable table, String objectKey, PlayerData player) {
        if (table == null) return;
        for (ExperienceItem item : table.items()) {
            int count = player.getClaimCount(claimKey(objectKey, item.id()));
            for (int i = 0; i < count; i++) for (Trigger trigger : item.triggers()) if (trigger.temporary()) trigger.apply(player);
        }
    }

    public ExperienceTable parseInline(String id, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException("Inline experience table must be a section");
        return parseTable(id, stringMap(map));
    }

    private static ExperienceTable parseTable(String id, Map<String, Object> section) {
        ArrayList<ExperienceItem> items = new ArrayList<>();
        for (Map.Entry<String, Object> entry : section.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> itemRaw)) throw new IllegalArgumentException("Experience item '" + entry.getKey() + "' is not a section");
            Map<String, Object> item = stringMap(itemRaw);
            Object rawTriggers = item.get("triggers");
            if (!(rawTriggers instanceof Collection<?>)) throw new IllegalArgumentException("Experience item '" + entry.getKey() + "' has no triggers");
            int period = integer(item.get("period"), 1);
            int firstTrigger = integer(item.get("first-trigger"), period);
            int lastTrigger = integer(item.get("last-trigger"), Integer.MAX_VALUE);
            int fixedLevel = integer(item.get("level"), -1);
            double chance = decimal(item.get("chance"), 100d) / 100d;
            double failReduction = decimal(item.get("fail-reduction"), 80d) / 100d;
            items.add(new ExperienceItem(entry.getKey(), period, firstTrigger, lastTrigger, fixedLevel,
                    chance, failReduction, NativeTriggerRegistry.parseAll(rawTriggers)));
        }
        return new ExperienceTable(id, List.copyOf(items));
    }

    private static String claimKey(String objectKey, String itemId) { return objectKey + "." + itemId; }
    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-'); }
    private static int integer(Object value, int fallback) {
        try { return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static double decimal(Object value, double fallback) {
        try { return value instanceof Number number ? number.doubleValue() : value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
