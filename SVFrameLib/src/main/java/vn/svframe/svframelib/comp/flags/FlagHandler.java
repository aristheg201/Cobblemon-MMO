package vn.svframe.svframelib.comp.flags;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Native Fabric flag dispatcher. Region/claim integrations register a FlagPlugin
 * and are combined with deny-wins semantics. MythicLib defaults are used only
 * when no provider exists for the queried flag.
 */
public class FlagHandler {
    private final List<FlagPlugin> flagPlugins = new CopyOnWriteArrayList<>();

    public void registerPlugin(FlagPlugin plugin) {
        if (plugin != null && !flagPlugins.contains(plugin)) flagPlugins.add(plugin);
    }

    public void unregisterPlugin(FlagPlugin plugin) {
        flagPlugins.remove(plugin);
    }

    public List<FlagPlugin> getPlugins() {
        return List.copyOf(flagPlugins);
    }

    public <T extends FlagPlugin> T getHandler(Class<T> type) {
        for (FlagPlugin plugin : flagPlugins) if (type.isInstance(plugin)) return type.cast(plugin);
        return null;
    }

    public boolean isPvpAllowed(ServerWorld world, BlockPos pos) {
        if (flagPlugins.isEmpty()) return true;
        for (FlagPlugin plugin : flagPlugins) if (!plugin.isPvpAllowed(world, pos)) return false;
        return true;
    }

    public boolean isFlagAllowed(ServerPlayerEntity player, CustomFlag flag) {
        if (flagPlugins.isEmpty()) return flag.getDefault();
        for (FlagPlugin plugin : flagPlugins) if (!plugin.isFlagAllowed(player, flag)) return false;
        return true;
    }

    public boolean isFlagAllowed(ServerWorld world, BlockPos pos, CustomFlag flag) {
        if (flagPlugins.isEmpty()) return flag.getDefault();
        for (FlagPlugin plugin : flagPlugins) if (!plugin.isFlagAllowed(world, pos, flag)) return false;
        return true;
    }
}
