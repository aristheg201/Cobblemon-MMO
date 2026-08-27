package vn.svframe.svframecore;

import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.module.MMOPlugin;
import vn.svframe.svframecore.rpg.SVFrameCoreClassModule;
import vn.svframe.svframecore.rpg.SVFrameCoreLevelModule;
import vn.svframe.svframecore.rpg.SVFrameCoreManaModule;

import java.nio.file.Path;
import java.util.logging.Logger;

public final class SVFrameCore extends MMOPlugin {
    public static final String ID = "svframecore";
    private static final Logger LOG = Logger.getLogger("SVFrameCore");
    private static SVFrameCore instance;
    private SVFrameCoreConfig config = new SVFrameCoreConfig(20L, 200L);

    private SVFrameCore() { }
    public static synchronized SVFrameCore bootstrap() { return instance == null ? (instance = new SVFrameCore()) : instance; }
    public static SVFrameCore inst() { return bootstrap(); }
    public static SVFrameCoreConfig config() { return inst().config; }
    public Path configRoot() { return FabricLoader.getInstance().getConfigDir().resolve("SVFrameCore"); }
    public Logger getLogger() { return LOG; }

    public void loadConfig() {
        try { config = SVFrameCoreConfig.load(configRoot().resolve("config.yml")); }
        catch (Exception exception) { throw new IllegalStateException("Could not load SVFrameCore config", exception); }
    }

    public void installRpgProviders() {
        SVFrameLib lib = SVFrameLib.inst();
        lib.setClassModule(new SVFrameCoreClassModule());
        lib.setLevelModule(new SVFrameCoreLevelModule());
        lib.setManaModule(new SVFrameCoreManaModule());
    }

    @Override public String getNamespacedKey() { return ID; }
    @Override public void debug(String message) { LOG.info(message); }
    @Override public void debug(String context, String message) { LOG.info("[" + context + "] " + message); }
}
