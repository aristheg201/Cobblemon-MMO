package vn.svframe.svquest.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svquest.SVQuest;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Feature ids and actions are administrator data, not quest Java constants. */
public final class FeatureCatalog {
    public record Feature(String command, String opener, boolean managed) {
        public Feature {
            command = command == null ? "" : command.trim();
            opener = opener == null ? "" : opener.trim();
        }
    }

    private static volatile Map<String, Feature> features = Map.of();
    private FeatureCatalog() {}

    public static synchronized int loadServer() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("svquest/features.json");
        LinkedHashMap<String, Feature> next = new LinkedHashMap<>();
        try {
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement root = JsonParser.parseReader(reader);
                    parseRoot(root, next);
                }
            }

            // Always use packaged defaults as the complete feature catalog baseline.
            // Administrator entries keep priority when present, while missing entries are filled so an
            // old/partial production features.json never turns valid quest buttons into "not configured".
            // Managed entries are integrations whose opening mechanism is dictated by the installed mod
            // itself (for example, a real world block interaction), so those intentionally override stale
            // command-based administrator entries.
            LinkedHashMap<String, Feature> packaged = loadPackagedDefaults();
            packaged.forEach((id, feature) -> {
                if (feature.managed()) next.put(id, feature);
                else next.putIfAbsent(id, feature);
            });

            features = Map.copyOf(next);
            SVQuest.LOGGER.info("SVQuest loaded {} feature actions from config/default merge.", features.size());
            return features.size();
        } catch (Exception error) {
            throw new IllegalStateException("Could not load config/svquest/features.json", error);
        }
    }

    private static LinkedHashMap<String, Feature> loadPackagedDefaults() throws Exception {
        LinkedHashMap<String, Feature> result = new LinkedHashMap<>();
        var container = FabricLoader.getInstance().getModContainer(SVQuest.MOD_ID).orElse(null);
        if (container == null) return result;
        for (Path root : container.getRootPaths()) {
            Path defaults = root.resolve("defaults/svquest/features.json");
            if (!Files.isRegularFile(defaults)) continue;
            try (Reader reader = Files.newBufferedReader(defaults, StandardCharsets.UTF_8)) {
                parseRoot(JsonParser.parseReader(reader), result);
            }
            break;
        }
        return result;
    }

    /**
     * Production servers may still have the pre-object schema where features.json is a top-level array.
     * Keep both formats valid so updating the mod never requires deleting/recreating administrator config.
     */
    private static void parseRoot(JsonElement root, LinkedHashMap<String, Feature> out) {
        if (root == null || root.isJsonNull()) return;
        if (root.isJsonArray()) {
            parseArray(root.getAsJsonArray(), out, "root");
            return;
        }
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("features.json root must be an object or array");
        }

        JsonObject object = root.getAsJsonObject();
        if (!object.has("features")) {
            parseMap(object, out, "root");
            return;
        }

        JsonElement entries = object.get("features");
        if (entries == null || entries.isJsonNull()) return;
        if (entries.isJsonObject()) {
            parseMap(entries.getAsJsonObject(), out, "features");
        } else if (entries.isJsonArray()) {
            parseArray(entries.getAsJsonArray(), out, "features");
        } else {
            throw new IllegalArgumentException("'features' must be an object or array");
        }
    }

    private static void parseMap(JsonObject entries, LinkedHashMap<String, Feature> out, String context) {
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            String id = normalizeId(entry.getKey(), context);
            Feature feature = parseFeature(entry.getValue(), context + "." + id);
            put(out, id, feature, context);
        }
    }

    private static void parseArray(JsonArray entries, LinkedHashMap<String, Feature> out, String context) {
        for (int i = 0; i < entries.size(); i++) {
            JsonElement element = entries.get(i);
            if (element == null || !element.isJsonObject()) {
                throw new IllegalArgumentException(context + "[" + i + "] must be an object");
            }
            JsonObject value = element.getAsJsonObject();
            String id = firstString(value, "id", "featureId", "feature_id", "key");
            if (id == null || id.isBlank()) {
                // Also accept [{"pokemon_skills":{"command":"pokeskill"}}] style legacy entries.
                if (value.entrySet().size() == 1) {
                    Map.Entry<String, JsonElement> only = value.entrySet().iterator().next();
                    String singletonId = normalizeId(only.getKey(), context + "[" + i + "]");
                    put(out, singletonId, parseFeature(only.getValue(), context + "[" + i + "]." + singletonId), context);
                    continue;
                }
                throw new IllegalArgumentException(context + "[" + i + "] is missing feature id (id/featureId/key)");
            }
            id = normalizeId(id, context + "[" + i + "]");
            put(out, id, parseFeature(value, context + "[" + i + "]"), context);
        }
    }

    private static Feature parseFeature(JsonElement element, String context) {
        if (element == null || element.isJsonNull()) return new Feature("", "", false);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new Feature(element.getAsString(), "", false);
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(context + " must be an object or command string");
        }
        JsonObject value = element.getAsJsonObject();
        return new Feature(firstString(value, "command"), firstString(value, "opener"), booleanValue(value, "managed"));
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                return value.getAsString();
            }
        }
        return "";
    }

    private static boolean booleanValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        try { return value != null && !value.isJsonNull() && value.getAsBoolean(); }
        catch (Exception ignored) { return false; }
    }

    private static String normalizeId(String id, String context) {
        String value = id == null ? "" : id.trim();
        if (value.isBlank()) throw new IllegalArgumentException(context + " contains a blank feature id");
        return value;
    }

    private static void put(LinkedHashMap<String, Feature> out, String id, Feature feature, String context) {
        if (out.putIfAbsent(id, feature) != null) {
            throw new IllegalArgumentException(context + " contains duplicate feature id '" + id + "'");
        }
    }

    public static Feature get(String id) { return id == null ? null : features.get(id); }
}
