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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Runtime progression settings loaded from config/svquest/settings.json. */
public final class ProgressSettings {
    private static volatile Values values;

    private ProgressSettings() {}

    public record Values(int pollIntervalTicks, int rankedHighElo, List<String> endgameRaidCategoryKeywords) {
        public Values {
            if (pollIntervalTicks <= 0) throw new IllegalArgumentException("pollIntervalTicks must be > 0");
            if (rankedHighElo < 0) throw new IllegalArgumentException("rankedHighElo must be >= 0");
            endgameRaidCategoryKeywords = List.copyOf(endgameRaidCategoryKeywords);
            if (endgameRaidCategoryKeywords.isEmpty()) throw new IllegalArgumentException("endgameRaidCategoryKeywords must not be empty");
        }
    }

    public static synchronized void loadServer() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("svquest/settings.json");
        if (!Files.isRegularFile(file)) throw new IllegalStateException("Missing config/svquest/settings.json");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int poll = requiredInt(root, "pollIntervalTicks");
            int elo = requiredInt(root, "rankedHighElo");
            JsonArray keywords = root.getAsJsonArray("endgameRaidCategoryKeywords");
            if (keywords == null || keywords.isEmpty()) throw new IllegalArgumentException("Missing endgameRaidCategoryKeywords");
            ArrayList<String> parsed = new ArrayList<>();
            for (JsonElement element : keywords) {
                String keyword = element.getAsString().trim().toLowerCase(Locale.ROOT);
                if (!keyword.isBlank()) parsed.add(keyword);
            }
            values = new Values(poll, elo, parsed);
            SVQuest.LOGGER.info("SVQuest progression settings loaded: poll={} ticks, rankedHighElo={}, raidKeywords={}", poll, elo, parsed);
        } catch (Exception error) {
            throw new IllegalStateException("Invalid config/svquest/settings.json", error);
        }
    }

    public static int pollIntervalTicks() { return require().pollIntervalTicks(); }
    public static int rankedHighElo() { return require().rankedHighElo(); }
    public static List<String> endgameRaidCategoryKeywords() { return require().endgameRaidCategoryKeywords(); }

    private static Values require() {
        Values current = values;
        if (current == null) throw new IllegalStateException("SVQuest ProgressSettings not loaded");
        return current;
    }

    private static int requiredInt(JsonObject root, String key) {
        if (!root.has(key)) throw new IllegalArgumentException("Missing " + key);
        return root.get(key).getAsInt();
    }
}
