package vn.svframe.svframemmo.cobblemon;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Dedicated-server bootstrap that keeps SVFrameMMO/SVFrameLib absent from the client dependency graph. */
public final class SVFrameMMOCobblemonServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("svframelib") || !loader.isModLoaded("svframemmo")) {
            throw new IllegalStateException("SVFrameMMO: Cobblemon Integration requires SVFrameLib and SVFrameMMO on the dedicated server");
        }
        new SVFrameMMOCobblemon().onInitialize();
    }
}
