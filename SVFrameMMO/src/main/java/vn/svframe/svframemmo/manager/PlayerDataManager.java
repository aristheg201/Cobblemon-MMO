package vn.svframe.svframemmo.manager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.config.DefaultFiles;
import vn.svframe.svframemmo.persistence.JsonPlayerDataStore;
import vn.svframe.svframemmo.persistence.MysqlPlayerDataStore;
import vn.svframe.svframemmo.persistence.PersistenceConfig;
import vn.svframe.svframemmo.persistence.PlayerDataSnapshot;
import vn.svframe.svframemmo.persistence.PlayerDataStore;
import vn.svframe.svframemmo.persistence.YamlPlayerDataStore;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Backend-independent native userdata manager with asynchronous persistence writes. */
public final class PlayerDataManager {
    private static final Logger LOG = Logger.getLogger("SVFrameMMO-PlayerData");
    private static final long WRITER_SHUTDOWN_SECONDS = 30L;

    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerData> online = new ConcurrentHashMap<>();
    private final Collection<PlayerData> allView = Collections.unmodifiableCollection(data.values());
    private final Collection<PlayerData> onlineView = Collections.unmodifiableCollection(online.values());
    private final AtomicReference<Throwable> asyncFailure = new AtomicReference<>();

    private Path worldRoot;
    private PersistenceConfig persistenceConfig;
    private volatile PlayerDataStore store;
    private volatile ExecutorService writer;
    private volatile boolean closing;

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
            startWriter();
            LOG.info("SVFrameMMO userdata backend: " + store.id() + ", records=" + data.size());
        } catch (Exception exception) {
            stopWriterNow();
            closeStore();
            throw new IllegalStateException("Could not initialize SVFrameMMO userdata backend", exception);
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) join(player);
    }

    public PlayerData join(ServerPlayerEntity player) {
        MMOPlayerData mmo = MMOPlayerData.getOrNull(player.getUuid());
        if (mmo != null && mmo.isOnline() && mmo.getPlayer() != player) mmo.updatePlayer(null);
        PlayerData value = data.computeIfAbsent(player.getUuid(), PlayerData::blank);
        if (!value.isOnline() || value.getPlayer() != player) value.attach(player);
        online.put(player.getUuid(), value);
        return value;
    }

    public void quit(ServerPlayerEntity player) {
        PlayerData value = data.get(player.getUuid());
        if (value == null) return;
        value.detach();
        online.remove(player.getUuid(), value);
        MMOPlayerData mmo = MMOPlayerData.getOrNull(player.getUuid());
        if (mmo != null && mmo.isOnline()) mmo.updatePlayer(null);
        savePlayer(value);
    }

    public PlayerData get(UUID id) { return data.computeIfAbsent(id, PlayerData::blank); }
    public PlayerData find(UUID id) { return data.get(id); }
    public PlayerData get(ServerPlayerEntity player) {
        PlayerData value = online.get(player.getUuid());
        return value != null && value.isOnline() && value.getPlayer() == player ? value : join(player);
    }

    /** All loaded records. This is a stable live view and does not allocate on iteration. */
    public Collection<PlayerData> all() { return allView; }

    /** Online records only. Tick runtimes must use this view instead of scanning the complete userdata cache. */
    public Collection<PlayerData> online() { return onlineView; }

    public synchronized String backendName() { return store == null ? "UNINITIALIZED" : store.id(); }

    /** Queues a complete snapshot write. Used by reload and explicit save paths. */
    public void save() {
        PlayerDataStore current = store;
        if (current == null || closing) return;
        Map<UUID, PlayerDataSnapshot> captured = snapshots(data.values());
        submit("full userdata save", target -> target.saveAll(captured));
    }

    /** Queues only currently online records. This is the normal autosave path. */
    public void saveOnline() {
        PlayerDataStore current = store;
        if (current == null || closing || online.isEmpty()) return;
        Map<UUID, PlayerDataSnapshot> captured = snapshots(online.values());
        if (!captured.isEmpty()) submit("online userdata save", target -> target.saveSome(captured));
    }

    /** Exports current in-memory state without changing the live backend. */
    public synchronized int exportTo(PersistenceConfig.Backend target) {
        if (store == null) throw new IllegalStateException("Player data backend is not initialized");
        PlayerDataStore destination = null;
        try {
            destination = createStore(target);
            Map<UUID, PlayerDataSnapshot> captured = snapshots(data.values());
            destination.saveAll(captured);
            return captured.size();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not export userdata to " + target, exception);
        } finally {
            if (destination != null && destination != store) try { destination.close(); } catch (Exception ignored) { }
        }
    }

    /** Drains asynchronous writes and performs one final complete, synchronous shutdown flush. */
    public void close() {
        final PlayerDataStore currentStore;
        final ExecutorService currentWriter;
        final Map<UUID, PlayerDataSnapshot> finalSnapshot;
        synchronized (this) {
            if (store == null) return;
            closing = true;
            currentStore = store;
            currentWriter = writer;
            writer = null;
            finalSnapshot = snapshots(data.values());
        }

        if (currentWriter != null) {
            currentWriter.shutdown();
            try {
                if (!currentWriter.awaitTermination(WRITER_SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                    LOG.warning("Timed out draining SVFrameMMO userdata writer; interrupting pending writes before final flush.");
                    currentWriter.shutdownNow();
                    currentWriter.awaitTermination(5L, TimeUnit.SECONDS);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                currentWriter.shutdownNow();
            }
        }

        RuntimeException failure = null;
        try {
            currentStore.saveAll(finalSnapshot);
        } catch (Exception exception) {
            failure = new IllegalStateException("Could not perform final SVFrameMMO userdata save to " + currentStore.id(), exception);
        } finally {
            try { currentStore.close(); }
            catch (Exception exception) {
                if (failure == null) failure = new IllegalStateException("Could not close SVFrameMMO userdata backend " + currentStore.id(), exception);
                else failure.addSuppressed(exception);
            }
            synchronized (this) {
                if (store == currentStore) store = null;
                closing = false;
            }
        }

        Throwable background = asyncFailure.getAndSet(null);
        if (background != null) LOG.log(Level.SEVERE, "At least one asynchronous SVFrameMMO userdata write failed before shutdown", background);
        if (failure != null) throw failure;
    }

    private void savePlayer(PlayerData value) {
        if (value == null || store == null || closing) return;
        UUID id = value.getUniqueId();
        PlayerDataSnapshot captured = PlayerDataSnapshot.capture(value);
        submit("userdata save for " + id, target -> target.saveOne(id, captured));
    }

    private void submit(String description, StoreWrite write) {
        ExecutorService currentWriter = writer;
        PlayerDataStore currentStore = store;
        if (currentWriter == null || currentStore == null || closing) return;
        try {
            currentWriter.execute(() -> {
                try {
                    write.run(currentStore);
                } catch (Throwable failure) {
                    asyncFailure.compareAndSet(null, failure);
                    LOG.log(Level.SEVERE, "Asynchronous SVFrameMMO " + description + " failed", failure);
                }
            });
        } catch (RejectedExecutionException rejected) {
            if (!closing) {
                asyncFailure.compareAndSet(null, rejected);
                LOG.log(Level.SEVERE, "SVFrameMMO userdata writer rejected " + description, rejected);
            }
        }
    }

    private synchronized void startWriter() {
        stopWriterNow();
        closing = false;
        asyncFailure.set(null);
        writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "SVFrameMMO-Userdata-IO");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, failure) -> LOG.log(Level.SEVERE, "Uncaught SVFrameMMO userdata writer failure", failure));
            return thread;
        });
    }

    private synchronized void stopWriterNow() {
        ExecutorService current = writer;
        writer = null;
        if (current != null) current.shutdownNow();
    }

    private void restoreAll(Map<UUID, PlayerDataSnapshot> loaded) {
        online.clear();
        data.clear();
        loaded.forEach((id, snapshot) -> {
            PlayerData value = PlayerData.blank(id);
            snapshot.apply(value);
            data.put(id, value);
        });
    }

    private static Map<UUID, PlayerDataSnapshot> snapshots(Collection<PlayerData> source) {
        LinkedHashMap<UUID, PlayerDataSnapshot> out = new LinkedHashMap<>();
        source.stream().sorted((a, b) -> a.getUniqueId().compareTo(b.getUniqueId()))
                .forEach(value -> out.put(value.getUniqueId(), PlayerDataSnapshot.capture(value)));
        return Map.copyOf(out);
    }

    private PlayerDataStore createStore(PersistenceConfig.Backend backend) throws Exception {
        if (worldRoot == null) throw new IllegalStateException("World root is not initialized");
        return switch (backend) {
            case JSON -> new JsonPlayerDataStore(worldRoot.resolve("svframemmo-playerdata.json"));
            case YAML -> new YamlPlayerDataStore(worldRoot.resolve(persistenceConfig.yamlDirectory()));
            case MYSQL -> new MysqlPlayerDataStore(persistenceConfig);
        };
    }

    private synchronized void closeStore() {
        if (store == null) return;
        try { store.close(); } catch (Exception exception) { LOG.warning("Could not close userdata backend: " + exception.getMessage()); }
        store = null;
    }

    @FunctionalInterface
    private interface StoreWrite {
        void run(PlayerDataStore store) throws Exception;
    }
}
