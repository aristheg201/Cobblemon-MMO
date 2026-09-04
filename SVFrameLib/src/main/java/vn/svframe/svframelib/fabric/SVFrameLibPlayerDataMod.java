package vn.svframe.svframelib.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.fabric.runtime.NativePlayerData;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Native Fabric lifecycle manager replacing MMOPlayerData's server-plugin platform player listener/static map. */
public final class SVFrameLibPlayerDataMod implements ModInitializer {
    private static final Map<UUID, NativePlayerData> PLAYER_DATA = new ConcurrentHashMap<>();
    private static final Map<UUID, NativePlayerData> ONLINE = new ConcurrentHashMap<>();
    private static volatile boolean initialized;
    private static long cleanupTicker;

    @Override public void onInitialize() { initialize(); }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            NativePlayerData data = setup(player.getUuid());
            data.updatePlayer(player);
            ONLINE.put(player.getUuid(), data);
            SVFrameLibHealthScale.onJoin(player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            UUID playerId = newPlayer.getUuid();
            NativePlayerData data = setup(playerId);
            data.updatePlayer(newPlayer);
            ONLINE.put(playerId, data);
            SVFrameLibHealthScale.onJoin(newPlayer);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            NativePlayerData data = getOrNull(player.getUuid());
            if (data != null) {
                data.shutdownSession();
                data.updatePlayer(null);
                ONLINE.remove(player.getUuid(), data);
            } else {
                ONLINE.remove(player.getUuid());
            }
            SVFrameLibCombatRuntime.clearPlayer(player.getUuid());
            SVFrameLibHealthScale.onDisconnect(player.getUuid());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (NativePlayerData data : ONLINE.values()) data.tickOnline();
            if ((++cleanupTicker & 31L) == 0L) flushOfflinePlayerData();
        });
    }

    public static NativePlayerData setup(UUID playerId) {
        return PLAYER_DATA.computeIfAbsent(playerId, id -> new NativePlayerData(false, id));
    }

    public static NativePlayerData setup(ServerPlayerEntity player) {
        NativePlayerData data = setup(player.getUuid());
        data.updatePlayer(player);
        return data;
    }

    public static NativePlayerData get(UUID playerId) {
        NativePlayerData data = PLAYER_DATA.get(playerId);
        if (data == null) throw new IllegalStateException("Player data is not loaded for " + playerId);
        return data;
    }

    public static NativePlayerData getOrNull(UUID playerId) { return playerId == null ? null : PLAYER_DATA.get(playerId); }
    public static boolean has(UUID playerId) { return playerId != null && PLAYER_DATA.containsKey(playerId); }
    public static Collection<NativePlayerData> loaded() { return List.copyOf(PLAYER_DATA.values()); }

    public static void forEach(Consumer<NativePlayerData> consumer) { PLAYER_DATA.values().forEach(consumer); }
    public static void forEachOnline(Consumer<NativePlayerData> consumer) {
        PLAYER_DATA.values().stream().filter(NativePlayerData::online).forEach(consumer);
    }
    public static void forEachPlaying(Consumer<NativePlayerData> consumer) {
        PLAYER_DATA.values().stream().filter(NativePlayerData::playing).forEach(consumer);
    }

    public static void flushOfflinePlayerData() {
        PLAYER_DATA.entrySet().removeIf(entry -> entry.getValue().timedOut());
    }

    public static void clear() { ONLINE.clear(); PLAYER_DATA.clear(); }
}
