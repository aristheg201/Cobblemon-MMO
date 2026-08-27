package vn.svframe.svframecore.player;

import vn.svframe.svframecore.SVFrameCore;
import vn.svframe.svframecore.api.player.PlayerData;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Native atomic file persistence for the currently ported player-data surface. */
public final class PlayerDataStorage {
    private PlayerDataStorage() { }

    public static void loadOrCreate(PlayerData data) {
        if (data.isInitialized()) return;
        Path file = file(data);
        try {
            if (Files.isRegularFile(file)) load(data, file);
            else SVFrameCore.config().defaultPlayerData().apply(data);
            data.markInitialized();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load SVFrameCore player data for " + data.getUniqueId(), exception);
        }
    }

    public static void save(PlayerData data) {
        if (!data.isInitialized()) return;
        Path file = file(data);
        try {
            Files.createDirectories(file.getParent());
            Properties properties = new Properties();
            properties.setProperty("level", Integer.toString(data.getLevel()));
            properties.setProperty("experience", Double.toString(data.getExperience()));
            properties.setProperty("class", data.getPlayerClass());
            properties.setProperty("class-points", Integer.toString(data.getClassPoints()));
            properties.setProperty("skill-points", Integer.toString(data.getSkillPoints()));
            properties.setProperty("attribute-points", Integer.toString(data.getAttributePoints()));
            properties.setProperty("attribute-realloc-points", Integer.toString(data.getAttributeReallocationPoints()));
            properties.setProperty("skill-realloc-points", Integer.toString(data.getSkillReallocationPoints()));
            properties.setProperty("skill-tree-realloc-points", Integer.toString(data.getSkillTreeReallocationPoints()));
            properties.setProperty("health", Double.toString(data.getPersistedHealth()));
            properties.setProperty("mana", Double.toString(data.getMana()));
            properties.setProperty("stamina", Double.toString(data.getStamina()));
            properties.setProperty("stellium", Double.toString(data.getStellium()));

            Path temporary = Files.createTempFile(file.getParent(), data.getUniqueId().toString(), ".tmp");
            try {
                try (OutputStream output = Files.newOutputStream(temporary)) {
                    properties.store(output, "SVFrameCore player data");
                }
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save SVFrameCore player data for " + data.getUniqueId(), exception);
        }
    }

    public static void saveAll() {
        for (PlayerData data : PlayerData.getAll()) if (data.isInitialized()) save(data);
    }

    private static void load(PlayerData data, Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        DefaultPlayerData defaults = SVFrameCore.config().defaultPlayerData();
        data.setLevel(integer(properties, "level", defaults.level()));
        data.setExperience(decimal(properties, "experience", 0d));
        data.setPlayerClass(properties.getProperty("class", ""));
        data.setClassPoints(integer(properties, "class-points", defaults.classPoints()));
        data.setSkillPoints(integer(properties, "skill-points", defaults.skillPoints()));
        data.setAttributePoints(integer(properties, "attribute-points", defaults.attributePoints()));
        data.setAttributeReallocationPoints(integer(properties, "attribute-realloc-points", defaults.attributeReallocationPoints()));
        data.setSkillReallocationPoints(integer(properties, "skill-realloc-points", defaults.skillReallocationPoints()));
        data.setSkillTreeReallocationPoints(integer(properties, "skill-tree-realloc-points", defaults.skillTreeReallocationPoints()));
        data.loadResources(
                decimal(properties, "health", defaults.health()),
                decimal(properties, "mana", defaults.mana()),
                decimal(properties, "stamina", defaults.stamina()),
                decimal(properties, "stellium", defaults.stellium()));
    }

    private static Path file(PlayerData data) {
        return SVFrameCore.inst().configRoot().resolve("playerdata").resolve(data.getUniqueId() + ".properties");
    }

    private static int integer(Properties properties, String key, int fallback) {
        try { return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static double decimal(Properties properties, String key, double fallback) {
        try { return Double.parseDouble(properties.getProperty(key, Double.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
