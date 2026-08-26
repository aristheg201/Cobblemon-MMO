package vn.svframe.svframelib.data;

import vn.svframe.svframelib.MythicLib;
import vn.svframe.svframelib.api.event.session.SessionUpdateEvent;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.data.queue.DataLoadResult;
import vn.svframe.svframelib.module.MMOPlugin;
import vn.svframe.svframelib.profile.ProfileSessionState;
import vn.svframe.svframelib.profile.SessionUpdateReason;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Fabric-native synchronized player data manager. Lifecycle behavior mirrors
 * MythicLib 1.7.1: join/setup, profile-session load/save, logout save and a
 * repeating autosave (30 minutes by default, with the original one-minute
 * minimum represented by {@link #setAutoSaveIntervalSeconds(long)}).
 */
public abstract class SynchronizedDataManager<H extends SynchronizedDataHolder, O extends OfflineDataHolder>
        implements vn.svframe.svframelib.util.Closeable {
    private static final long DEFAULT_AUTOSAVE_SECONDS = 1800L;
    private static final long MIN_AUTOSAVE_SECONDS = 60L;

    private final MMOPlugin owning;
    private final Map<UUID, H> activeData = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Database<H, O> database;
    private volatile boolean autoSaveEnabled = true;
    private volatile long autoSaveIntervalSeconds = DEFAULT_AUTOSAVE_SECONDS;

    public SynchronizedDataManager(MMOPlugin plugin) { this(plugin, true); }
    public SynchronizedDataManager(MMOPlugin plugin, boolean ignored) { owning = Objects.requireNonNull(plugin); }

    public Database<H, O> getDatabase() { return database; }

    public synchronized void setupDatabase(Supplier<Database<H, O>> primary, Supplier<Database<H, O>> fallback) {
        RuntimeException first = null;
        try { setupDatabase(primary.get()); return; }
        catch (RuntimeException exception) { first = exception; }
        if (fallback == null) throw first;
        owning.logger().warning("Primary database failed, using fallback: " + first.getMessage());
        setupDatabase(fallback.get());
    }

    public synchronized void setupDatabase(Database<H, O> value) {
        Objects.requireNonNull(value, "database");
        if (database != null) database.close();
        value.setup();
        database = value;
    }

    public MMOPlugin getOwningPlugin() { return owning; }
    public H get(ServerPlayerEntity player) { return get(player.getUuid()); }
    public H get(UUID id) { return Objects.requireNonNull(activeData.get(id), "Player data not loaded: " + id); }
    public H getOrNull(ServerPlayerEntity player) { return player == null ? null : getOrNull(player.getUuid()); }
    public H getOrNull(UUID id) { return activeData.get(id); }
    public O getOffline(UUID id) { Database<H, O> db = database; return db == null ? null : db.getOffline(id); }

    public void setAutoSaveEnabled(boolean enabled) { autoSaveEnabled = enabled; }
    public boolean isAutoSaveEnabled() { return autoSaveEnabled; }
    public void setAutoSaveIntervalSeconds(long seconds) { autoSaveIntervalSeconds = Math.max(MIN_AUTOSAVE_SECONDS, seconds); }
    public long getAutoSaveIntervalSeconds() { return autoSaveIntervalSeconds; }

    public void autosave() {
        for (H data : List.copyOf(activeData.values())) {
            if (data.shouldBeSaved() && data.getMMOPlayerData().isPlaying()) saveData(data, SessionUpdateReason.AUTOSAVE);
        }
    }

    /** Registers the native Fabric lifecycle once and activates current online players. */
    public void initialize() {
        if (!initialized.compareAndSet(false, true)) return;
        closed.set(false);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onEnable(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onQuit(handler.getPlayer()));

        if (!owning.isProfilePlugin()) {
            SessionUpdateEvent.EVENT.register(this::onSessionUpdate);
        }

        MinecraftServer server = MythicLibFabricMod.server();
        if (server != null) for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) onEnable(player);

        scheduleNextAutosave();
    }

    private void scheduleNextAutosave() {
        if (closed.get() || !autoSaveEnabled) return;
        long ticksLong = Math.max(MIN_AUTOSAVE_SECONDS, autoSaveIntervalSeconds) * 20L;
        int ticks = (int) Math.min(Integer.MAX_VALUE, ticksLong);
        MythicLibFabricMod.schedule(ticks, () -> {
            if (closed.get()) return;
            try { autosave(); }
            catch (RuntimeException exception) { owning.logger().warning("Could not autosave synchronized data: " + exception.getMessage()); }
            scheduleNextAutosave();
        });
    }

    protected void onEnable(ServerPlayerEntity player) {
        H data = setup(player);
        if (owning.isProfilePlugin() && !data.isSessionReady()
                && MythicLib.plugin.getProfileMode() != vn.svframe.svframelib.comp.profile.ProfileMode.PROXY) {
            loadData(data);
        }
    }

    protected void onQuit(ServerPlayerEntity player) {
        H data = activeData.remove(player.getUuid());
        if (data == null) return;
        if (owning.isProfilePlugin() && data.isSessionReady()) saveData(data, SessionUpdateReason.LOG_OUT);
    }

    protected void onSessionUpdate(SessionUpdateEvent event) {
        if (closed.get()) return;
        UUID id = event.getPlayerData().getUniqueId();
        ProfileSessionState state = event.getNewState();
        if (state == ProfileSessionState.OPENING) {
            H data = get(id);
            if (data.isSessionReady()) throw new IllegalStateException("Player data already loaded: " + id);
            loadData(data);
        } else if (state.isClosing()) {
            H data = get(id);
            if (data.isSessionReady()) saveData(data, event.getReason());
        } else if (state.isDead()) {
            activeData.computeIfPresent(id, (uuid, old) -> newPlayerData(event.getPlayerData()));
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        saveAll(true);
        Database<H, O> db = database;
        if (db != null) db.close();
        activeData.clear();
    }

    public CompletableFuture<Void> loadData(H data) {
        return CompletableFuture.runAsync(() -> {
            Database<H, O> db = database;
            if (db == null) {
                loadEmptyPlayerData(data);
                data.markSessionReady();
                return;
            }
            DataLoadResult result = db.loadData(data, false);
            if (result == null || result.isEmpty()) loadEmptyPlayerData(data);
            data.markSessionReady();
            db.confirmReception(data);
        });
    }

    public CompletableFuture<Void> saveData(H data, SessionUpdateReason reason) {
        Objects.requireNonNull(reason, "reason");
        boolean shouldSave = data.shouldBeSaved();
        data.onSaved(reason);
        return CompletableFuture.runAsync(() -> {
            Database<H, O> db = database;
            if (db != null && shouldSave) db.saveData(data, reason);
        });
    }

    /** 1.7.1 setup only attaches a holder; lifecycle decides when to load it. */
    public H setup(ServerPlayerEntity player) {
        MMOPlayerData playerData = MMOPlayerData.setup(player);
        return activeData.computeIfAbsent(player.getUuid(), id -> newPlayerData(playerData));
    }

    public H setup(UUID id) {
        MMOPlayerData playerData = MMOPlayerData.has(id) ? MMOPlayerData.get(id) : MMOPlayerData.setup(id);
        return activeData.computeIfAbsent(id, key -> newPlayerData(playerData));
    }

    public abstract H newPlayerData(MMOPlayerData playerData);
    public abstract void loadEmptyPlayerData(H data);
    public abstract Object newProfileDataModule();

    public boolean isLoaded(UUID id) { return activeData.containsKey(id); }
    public Collection<H> getLoaded() { return Collections.unmodifiableCollection(activeData.values()); }
    public void initialize(Object ignoredA, Object ignoredB) { initialize(); }
    public void fake(H data) { activeData.put(data.getUniqueId(), data); }
    public H unregister(ServerPlayerEntity player, SaveReason reason) { return unregister(player, reason.adapt()); }

    public H unregister(ServerPlayerEntity player, SessionUpdateReason reason) {
        Objects.requireNonNull(reason, "reason");
        H data;
        if (reason == SessionUpdateReason.LOG_OUT) {
            data = activeData.remove(player.getUuid());
        } else if (reason == SessionUpdateReason.QUIT_PROFILE || reason == SessionUpdateReason.SWITCH_PROFILE) {
            data = activeData.compute(player.getUuid(), (uuid, old) -> newPlayerData(MMOPlayerData.get(uuid)));
        } else {
            throw new IllegalArgumentException("Unsupported unregister reason: " + reason);
        }
        if (data != null && data.isSessionReady()) saveData(data, reason).join();
        return data;
    }

    public H setupEmpty(ServerPlayerEntity player) {
        MMOPlayerData playerData = MMOPlayerData.setup(player);
        H data = activeData.computeIfAbsent(player.getUuid(), id -> newPlayerData(playerData));
        loadEmptyPlayerData(data);
        data.markSessionReady();
        return data;
    }

    public void saveAll(boolean closing) {
        for (H data : List.copyOf(activeData.values())) {
            if (data.shouldBeSaved()) saveData(data, closing ? SessionUpdateReason.LOG_OUT : SessionUpdateReason.AUTOSAVE).join();
        }
    }
}
