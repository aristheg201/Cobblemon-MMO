package vn.svframe.svframemmo.cobblemon;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.moves.Moves;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.config.IntegrationConfig;
import vn.svframe.svframemmo.cobblemon.fusion.FusionLockHooks;
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;
import vn.svframe.svframemmo.cobblemon.fusion.PotaraUseHandler;
import vn.svframe.svframemmo.cobblemon.item.PotaraTierResolver;

/** Native server-side Cobblemon bridge owned by SVFrameMMO. */
public final class SVFrameMMOCobblemon implements ModInitializer {
    public static final String ID = "svframemmo_cobblemon";
    public static final Logger LOG = LoggerFactory.getLogger("SVFrameMMO: Cobblemon Integration");
    private static volatile IntegrationConfig config;
    private static final PotaraTierResolver POTARA = new PotaraTierResolver();
    private static final FusionService FUSIONS = new FusionService();

    @Override public void onInitialize() {
        try { config = IntegrationConfig.load(); }
        catch (Exception error) { throw new IllegalStateException("Could not load SVFrameMMO Cobblemon integration config", error); }

        if (Moves.count() > 0) FUSIONS.reloadMoveDefinitions();
        CobblemonEvents.COBBLEMON_INITIALISED.subscribe(ignored -> FUSIONS.reloadMoveDefinitions());
        Moves.INSTANCE.getObservable().subscribe(ignored -> FUSIONS.reloadMoveDefinitions());
        FusionLockHooks.register(FUSIONS);
        PotaraUseHandler.register(FUSIONS);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !FUSIONS.blocksDamage(entity));
        ServerTickEvents.END_SERVER_TICK.register(server -> FUSIONS.tick(SVFrameMMO.currentTick(), server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FUSIONS.onDisconnect(handler.player));
        LOG.info("SVFrameMMO: Cobblemon Integration online; moves={}, Potara uses deployed-party right click", FUSIONS.moveDefinitionCount());
    }

    public static IntegrationConfig config() {
        IntegrationConfig value = config;
        if (value == null) throw new IllegalStateException("SVFrameMMO: Cobblemon Integration is not initialized");
        return value;
    }

    public static PotaraTierResolver potara() { return POTARA; }
    public static FusionService fusions() { return FUSIONS; }
}
