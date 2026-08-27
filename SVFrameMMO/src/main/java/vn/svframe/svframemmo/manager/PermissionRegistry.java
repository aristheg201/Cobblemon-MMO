package vn.svframe.svframemmo.manager;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Native named-permission bridge. Integrations can register predicates without platform shims. */
public final class PermissionRegistry {
    private final Map<String, Predicate<ServerPlayerEntity>> nodes = new LinkedHashMap<>();

    public synchronized AutoCloseable register(String permission, Predicate<ServerPlayerEntity> predicate) {
        String key = normalize(permission);
        Objects.requireNonNull(predicate, "predicate");
        if (nodes.putIfAbsent(key, predicate) != null) throw new IllegalStateException("Permission already registered: " + permission);
        return () -> { synchronized (PermissionRegistry.this) { nodes.remove(key, predicate); } };
    }

    public synchronized boolean has(ServerPlayerEntity player, String permission) {
        if (permission == null || permission.isBlank()) return true;
        if (player == null) return false;
        Predicate<ServerPlayerEntity> predicate = nodes.get(normalize(permission));
        if (predicate != null) return predicate.test(player);
        return player.getServer().getPlayerManager().isOperator(player.getGameProfile());
    }

    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }
}
