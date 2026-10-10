package vn.svframe.svframemmo.player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Transient admin-only gameplay toggles. These deliberately do not persist across reconnects. */
public final class AdminRuntimeState {
    private static final Set<UUID> NO_COOLDOWN = ConcurrentHashMap.newKeySet();

    private AdminRuntimeState() { }

    public static boolean isNoCooldown(UUID playerId) { return NO_COOLDOWN.contains(playerId); }

    public static boolean toggleNoCooldown(UUID playerId) {
        if (NO_COOLDOWN.remove(playerId)) return false;
        NO_COOLDOWN.add(playerId);
        return true;
    }

    public static void clear(UUID playerId) { NO_COOLDOWN.remove(playerId); }
}
