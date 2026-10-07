package vn.svframe.svframelib.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native Fabric permission lookup with optional LuckPerms synchronization and
 * vanilla operator fallback. Transient SVFrame grants remain functional even
 * when LuckPerms is not installed, including integrated singleplayer servers.
 */
public final class SVFrameLibPermissionBridge {
    private static final Map<UUID, Set<String>> TRANSIENT_GRANTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Object>> LUCKPERMS_NODES = new ConcurrentHashMap<>();

    private SVFrameLibPermissionBridge() { }

    public static boolean has(ServerPlayerEntity player, String permission) {
        if (permission == null || permission.isBlank()) return true;
        if (player == null) return false;

        String normalized = normalize(permission);
        Set<String> local = TRANSIENT_GRANTS.get(player.getUuid());
        if (local != null && local.contains(normalized)) return true;

        Boolean luckPerms = luckPermsHas(player.getUuid(), normalized);
        if (luckPerms != null) return luckPerms;
        return player.hasPermissionLevel(2);
    }

    public static void grantTransient(UUID playerId, String permission) {
        if (playerId == null || permission == null || permission.isBlank()) return;
        String normalized = normalize(permission);
        TRANSIENT_GRANTS.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(normalized);
        syncLuckPermsGrant(playerId, normalized);
    }

    public static void revokeTransient(UUID playerId, String permission) {
        if (playerId == null || permission == null || permission.isBlank()) return;
        String normalized = normalize(permission);

        Set<String> local = TRANSIENT_GRANTS.get(playerId);
        if (local != null) {
            local.remove(normalized);
            if (local.isEmpty()) TRANSIENT_GRANTS.remove(playerId, local);
        }
        syncLuckPermsRevoke(playerId, normalized);
    }

    public static void clearTransient(UUID playerId) {
        if (playerId == null) return;
        TRANSIENT_GRANTS.remove(playerId);
        Map<String, Object> nodes = LUCKPERMS_NODES.remove(playerId);
        if (nodes == null || nodes.isEmpty()) return;

        Object user = luckPermsUser(playerId);
        if (user == null) return;
        Object transientData = invokeNoArg(user, "transientData");
        if (transientData == null) return;
        for (Object node : nodes.values()) invokeSingleArg(transientData, "remove", node);
    }

    private static Boolean luckPermsHas(UUID playerId, String permission) {
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) return null;
        Object user = luckPermsUser(playerId);
        if (user == null) return null;
        try {
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            Object result = permissionData.getClass().getMethod("checkPermission", String.class)
                    .invoke(permissionData, permission);
            Method asBoolean = result.getClass().getMethod("asBoolean");
            return Boolean.TRUE.equals(asBoolean.invoke(result));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void syncLuckPermsGrant(UUID playerId, String permission) {
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) return;
        Object user = luckPermsUser(playerId);
        if (user == null) return;

        Object node = createLuckPermsNode(permission);
        if (node == null) return;
        Object transientData = invokeNoArg(user, "transientData");
        if (transientData == null || !invokeSingleArg(transientData, "add", node)) return;

        Map<String, Object> nodes = LUCKPERMS_NODES.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        Object previous = nodes.put(permission, node);
        if (previous != null && previous != node) invokeSingleArg(transientData, "remove", previous);
    }

    private static void syncLuckPermsRevoke(UUID playerId, String permission) {
        Map<String, Object> nodes = LUCKPERMS_NODES.get(playerId);
        Object node = nodes == null ? null : nodes.remove(permission);
        if (nodes != null && nodes.isEmpty()) LUCKPERMS_NODES.remove(playerId, nodes);
        if (node == null) return;

        Object user = luckPermsUser(playerId);
        if (user == null) return;
        Object transientData = invokeNoArg(user, "transientData");
        if (transientData != null) invokeSingleArg(transientData, "remove", node);
    }

    private static Object luckPermsUser(UUID playerId) {
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) return null;
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            return userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, playerId);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Object createLuckPermsNode(String permission) {
        try {
            Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
            Object builder = nodeClass.getMethod("builder", String.class).invoke(null, permission);
            builder = builder.getClass().getMethod("value", boolean.class).invoke(builder, true);
            return builder.getClass().getMethod("build").invoke(builder);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean invokeSingleArg(Object target, String methodName, Object argument) {
        if (target == null || argument == null) return false;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) continue;
            if (!method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) continue;
            try {
                method.invoke(target, argument);
                return true;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    private static String normalize(String permission) {
        return permission.trim().toLowerCase(Locale.ROOT);
    }
}
