package vn.svframe.svquest.server;

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

/** Feature ids and commands are administrator data, not Java constants. */
public final class FeatureCatalog {
    public record Feature(String command, String opener) {
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
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonObject entries = root.has("features") ? root.getAsJsonObject("features") : root;
                    for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                        JsonObject value = entry.getValue().getAsJsonObject();
                        next.put(entry.getKey(), new Feature(
                                value.has("command") ? value.get("command").getAsString() : "",
                                value.has("opener") ? value.get("opener").getAsString() : ""
                        ));
                    }
                }
            }
            features = Map.copyOf(next);
            SVQuest.LOGGER.info("SVQuest loaded {} feature actions from config.", features.size());
            return features.size();
        } catch (Exception error) {
            throw new IllegalStateException("Could not load config/svquest/features.json", error);
        }
    }

    public static Feature get(String id) { return id == null ? null : features.get(id); }
}
