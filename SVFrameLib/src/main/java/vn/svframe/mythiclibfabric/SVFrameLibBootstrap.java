package vn.svframe.mythiclibfabric;

import io.lumine.mythic.lib.MythicLib;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.util.logging.Logger;

/** Initializes the public 1.7.1 compatibility facade before runtime modules. */
public final class SVFrameLibBootstrap implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("SVFrameLib");
    @Override public void onInitialize() {
        MythicLib.bootstrap().onLoad();
        MythicLib.plugin.onEnable();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> LOG.info("SVFrameLib online"));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> MythicLib.plugin.onDisable());
    }
}
