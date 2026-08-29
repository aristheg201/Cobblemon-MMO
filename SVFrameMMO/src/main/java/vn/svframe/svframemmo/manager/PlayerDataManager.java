package vn.svframe.svframemmo.manager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import vn.svframe.svframemmo.config.DefaultFiles;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.persistence.JsonPlayerDataStore;
import vn.svframe.svframemmo.persistence.MysqlPlayerDataStore;
import vn.svframe.svframemmo.persistence.PersistenceConfig;
import vn.svframe.svframemmo.persistence.PlayerDataSnapshot;
import vn.svframe.svframemmo.persistence.PlayerDataStore;
import vn.svframe.svframemmo.persistence.YamlPlayerDataStore;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Backend-independent native userdata manager with JSON migration, YAML and MySQL storage. */
public final class PlayerDataManager {
    private static final Logger LOG = Logger.getLogger("SVFrameMMO-PlayerData");
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private Path worldRoot;
    private PersistenceConfig persistenceConfig;
    private PlayerDataStore store;

    public synchronized void start(MinecraftServer server) {
        closeStore();
        worldRoot = server.getSavePath(WorldSavePath.ROOT);
        try {
            persistenceConfig = PersistenceConfig.load(DefaultFiles.ROOT.resolve("config.yml"));
            store = createStore(persistenceConfig.backend());
            Map<UUID, PlayerDataSnapshot> loaded = store.loadAll();
            JsonPlayerDataStore legacy = new JsonPlayerDataStore(worldRoot.resolve("svframemmo-playerdata.json"));
            if (persistenceConfig.backend() != PersistenceConfig.Backend.JSON && persistenceConfig.autoMigrateJson()
                    && loaded.isEmpty() && legacy.exists()) {
                Map<UUID, PlayerDataSnapshot> migrated = legacy.loadAll();
                if (!migrated.isEmpty()) {
                    store.saveAll(migrated);
                    loaded = migrated;
                    LOG.info("Migrated " + migrated.size() + " native JSON userdata records to " + store.id() + ".");
                }
            }
            restoreAll(loaded);
            LOG.info("SVFrameMMO userdata backend: " + store.id() + ", records=" + data.size());
        } catch (Exception exception) {
            closeStore();
            throw new IllegalStateException("Could not initialize SVFrameMMO userdata backend", exception);
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) join(player);
    }

    public PlayerData join(ServerPlayerEntity player) {
        PlayerData value = data.computeIfAbsent(player.getUuid(), PlayerData::blank);
        value.attach(player);
        return value;
    }

    public void quit(ServerPlayerEntity player) {
        PlayerData value = data.get(player.getUuid());
        if (value != null) { value.detach(); save(); }
    }

    public PlayerData get(UUID id) { return data.computeIfAbsent(id, PlayerData::blank); }
    public PlayerData find(UUID id) { return data.get(id); }
    public PlayerData get(ServerPlayerEntity player) { return join(player); }
    public Collection<PlayerData> all() { return List.copyOf(data.values()); }
    public synchronized String backendName() { return store == null ? "UNINITIALIZED" : store.id(); }

    public synchronized void save() {
        if (store == null) return;
        try { store.saveAll(snapshots()); }
        catch (Exception exception) { throw new IllegalStateException("Could not save SVFrameMMO player data to " + store.id(), exception); }
    }

    /** Exports current in-memory state without changing the live backend. */
    public synchronized int exportTo(PersistenceConfig.Backend target) {
        if (store == null) throw new IllegalStateException("Player data backend is not initialized");
        PlayerDataStore destination = null;
        try {
            destination = createStore(target);
            Map<UUID, PlayerDataSnapshot> snapshots = snapshots();
            destination.saveAll(snapshots);
            return snapshots.size();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not export userdata to " + target, exception);
        } finally {
            if (destination != null && destination != store) try { destination.close(); } catch (Exception ignored) { }
        }
    }

    public synchronized void close() { save(); closeStore(); }

    private void restoreAll(Map<UUID, PlayerDataSnapshot> loaded) {
        data.clear();
        loaded.forEach((id, snapshot) -> {
            PlayerData value = PlayerData.blank(id);
            snapshot.apply(value);
            data.put(id, value);
        });
    }

    private Map<UUID, PlayerDataSnapshot> snapshots() {
        LinkedHashMap<UUID, PlayerDataSnapshot> out = new LinkedHashMap<>();
        data.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> out.put(entry.getKey(), PlayerDataSnapshot.capture(entry.getValue())));
        return out;
    }

    private PlayerDataStore createStore(PersistenceConfig.Backend backend) throws Exception {
        if (worldRoot == null) throw new IllegalStateException("World root is not initialized");
        return switch (backend) {
            case JSON -> new JsonPlayerDataStore(worldRoot.resolve("svframemmo-playerdata.json"));
            case YAML -> new YamlPlayerDataStore(worldRoot.resolve(persistenceConfig.yamlDirectory()));
            case MYSQL -> new MysqlPlayerDataStore(persistenceConfig);
        };
    }

    private void closeStore() {
        if (store == null) return;
        try { store.close(); } catch (Exception exception) { LOG.warning("Could not close userdata backend: " + exception.getMessage()); }
        store = null;
    }
}
