package vn.svframe.svframemmo.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** JSON persistence backend for native SVFrameMMO player state. */
public final class PlayerDataManager {
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Path file;

    public void start(MinecraftServer server) {
        file = server.getSavePath(WorldSavePath.ROOT).resolve("svframemmo-playerdata.json");
        load();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) join(player);
    }

    public PlayerData join(ServerPlayerEntity player) {
        PlayerData value = data.computeIfAbsent(player.getUuid(), PlayerData::blank);
        value.attach(player);
        return value;
    }

    public void quit(ServerPlayerEntity player) {
        PlayerData value = data.get(player.getUuid());
        if (value != null) {
            value.detach();
            save();
        }
    }

    public PlayerData get(UUID id) { return data.computeIfAbsent(id, PlayerData::blank); }
    public PlayerData get(ServerPlayerEntity player) { return join(player); }
    public Collection<PlayerData> all() { return List.copyOf(data.values()); }

    public synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Map<String, Saved> out = new TreeMap<>();
            for (Map.Entry<UUID, PlayerData> entry : data.entrySet()) {
                PlayerData value = entry.getValue();
                out.put(entry.getKey().toString(), new Saved(
                        value.getClassId(), value.getLevel(), value.getExperience(),
                        value.getClassPoints(), value.getSkillPoints(), value.getAttributePoints(),
                        value.getMana(), value.getStamina(), value.getStellium(),
                        value.getAttributes().mapPoints(), value.getSkillLevels()));
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(out));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not save SVFrameMMO player data", exception);
        }
    }

    private synchronized void load() {
        data.clear();
        if (file == null || !Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                UUID id = UUID.fromString(entry.getKey());
                Saved saved = gson.fromJson(entry.getValue(), Saved.class);
                PlayerData value = PlayerData.blank(id);
                value.restore(saved.playerClass, saved.level, saved.experience,
                        saved.classPoints, saved.skillPoints, saved.attributePoints,
                        saved.mana, saved.stamina, saved.stellium,
                        saved.attributes, saved.skills);
                data.put(id, value);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load SVFrameMMO player data", exception);
        }
    }

    private record Saved(String playerClass, int level, double experience,
                         int classPoints, int skillPoints, int attributePoints,
                         double mana, double stamina, double stellium,
                         Map<String, Integer> attributes,
                         Map<String, Integer> skills) {}
}
