package vn.svframe.svframelib.comp.flags;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric region-flag bridge retaining the MythicLib WorldGuard-facing class
 * name without introducing a Bukkit/WorldGuard runtime dependency. A native
 * region mod can populate world-level overrides through this provider.
 */
public class WorldGuardFlags implements FlagPlugin {
    private final Map<String, Boolean> overrides = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pvpOverrides = new ConcurrentHashMap<>();

    /** Compatibility name: returns the canonical native flag key. */
    public String toWorldGuard(CustomFlag flag) {
        return flag.getPath();
    }

    public void setOverride(String world, CustomFlag flag, boolean allowed) {
        overrides.put(key(world, flag), allowed);
    }

    public void clearOverride(String world, CustomFlag flag) {
        overrides.remove(key(world, flag));
    }

    public void setPvpOverride(String world, boolean allowed) {
        pvpOverrides.put(world, allowed);
    }

    public void clearPvpOverride(String world) {
        pvpOverrides.remove(world);
    }

    @Override
    public boolean isPvpAllowed(ServerWorld world, BlockPos pos) {
        return pvpOverrides.getOrDefault(world.getRegistryKey().getValue().toString(), true);
    }

    @Override
    public boolean isFlagAllowed(ServerPlayerEntity player, CustomFlag flag) {
        return resolve(player.getServerWorld(), flag);
    }

    @Override
    public boolean isFlagAllowed(ServerWorld world, BlockPos pos, CustomFlag flag) {
        return resolve(world, flag);
    }

    private boolean resolve(ServerWorld world, CustomFlag flag) {
        Boolean value = overrides.get(key(world.getRegistryKey().getValue().toString(), flag));
        return value == null ? flag.getDefault() : value;
    }

    private static String key(String world, CustomFlag flag) {
        return world + "|" + flag.name();
    }
}
