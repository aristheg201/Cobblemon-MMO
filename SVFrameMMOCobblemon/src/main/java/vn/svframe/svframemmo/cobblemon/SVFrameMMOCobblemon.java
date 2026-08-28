package vn.svframe.svframemmo.cobblemon;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.svframemmo.cobblemon.config.IntegrationConfig;
import vn.svframe.svframemmo.cobblemon.item.PotaraTierResolver;

/** Native server-side Cobblemon bridge owned by SVFrameMMO. */
public final class SVFrameMMOCobblemon implements ModInitializer {
    public static final String ID = "svframemmo_cobblemon";
    public static final Logger LOG = LoggerFactory.getLogger("SVFrameMMO: Cobblemon Integration");
    private static volatile IntegrationConfig config;
    private static final PotaraTierResolver POTARA = new PotaraTierResolver();

    @Override public void onInitialize() {
        try { config = IntegrationConfig.load(); }
        catch (Exception error) { throw new IllegalStateException("Could not load SVFrameMMO Cobblemon integration config", error); }
        LOG.info("SVFrameMMO: Cobblemon Integration Potara definitions loaded from vanilla item + CustomModelData config");
    }

    public static IntegrationConfig config() {
        IntegrationConfig value = config;
        if (value == null) throw new IllegalStateException("SVFrameMMO: Cobblemon Integration is not initialized");
        return value;
    }

    public static PotaraTierResolver potara() { return POTARA; }
}
