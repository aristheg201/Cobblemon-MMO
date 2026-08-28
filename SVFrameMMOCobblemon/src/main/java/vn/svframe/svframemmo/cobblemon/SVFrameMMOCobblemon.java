package vn.svframe.svframemmo.cobblemon;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.moves.Moves;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.svframelib.api.event.skill.PlayerCastSkillEvent;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.config.IntegrationConfig;
import vn.svframe.svframemmo.cobblemon.fusion.FusionCommands;
import vn.svframe.svframemmo.cobblemon.fusion.FusionLockHooks;
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;
import vn.svframe.svframemmo.cobblemon.fusion.PotaraUseHandler;
import vn.svframe.svframemmo.cobblemon.integration.CobblemonMoveVfxService;
import vn.svframe.svframemmo.cobblemon.integration.LuckPermsIntegration;
import vn.svframe.svframemmo.cobblemon.integration.PlaceholderIntegration;
import vn.svframe.svframemmo.cobblemon.item.PotaraTierResolver;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkill;

/** Separate native server-side integration mod bridging Cobblemon/Mega Showdown to SVFrameMMO. */
public final class SVFrameMMOCobblemon implements ModInitializer {
    public static final String ID = "svframemmo_cobblemon";
    public static final Logger LOG = LoggerFactory.getLogger("SVFrameMMO: Cobblemon Integration");
    private static volatile IntegrationConfig config;
    private static final PotaraTierResolver POTARA = new PotaraTierResolver();
    private static final FusionService FUSIONS = new FusionService();
    private static final CobblemonMoveVfxService MOVE_VFX = new CobblemonMoveVfxService();

    @Override public void onInitialize() {
        try { config = IntegrationConfig.load(); }
        catch (Exception error) { throw new IllegalStateException("Could not load SVFrameMMO Cobblemon integration config", error); }

        // Idempotent: normally registered by the early SVFrameMMO entrypoint before class YAML is parsed.
        FUSIONS.registerMoveSkillSource();
        MOVE_VFX.reload();
        LuckPermsIntegration.initialize();
        PlaceholderIntegration.registerIfPresent();
        if (Moves.count() > 0) FUSIONS.reloadMoveDefinitions();
        CobblemonEvents.COBBLEMON_INITIALISED.subscribe(ignored -> {
            FUSIONS.reloadMoveDefinitions();
            MOVE_VFX.reload();
        });
        Moves.INSTANCE.getObservable().subscribe(ignored -> FUSIONS.reloadMoveDefinitions());
        FusionLockHooks.register(FUSIONS);
        PotaraUseHandler.register(FUSIONS);
        PlayerCastSkillEvent.EVENT.register(event -> {
            if (event.isCancelled() || event.getResult() == null || !event.getResult().isSuccessful(event.getMetadata())) return;
            if (event.getCast().getHandler() instanceof CobblemonMoveSkill move)
                MOVE_VFX.renderActor(event.getPlayer(), move.template());
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FusionCommands.register(dispatcher, FUSIONS));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !FUSIONS.blocksDamage(entity));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> FUSIONS.cooldowns().start(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> FUSIONS.cooldowns().stop());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long tick = SVFrameMMO.currentTick();
            FUSIONS.tick(tick, server);
            FUSIONS.cooldowns().tick(tick);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FUSIONS.onDisconnect(handler.player));
        LOG.info("Cobblemon Integration online; generatedMoves={}, moveVfxPlans={}, Potara cooldown={}s, Fusion Dance={}s/{}s cooldown",
                FUSIONS.moveDefinitionCount(), MOVE_VFX.planCount(), config.fusion.potaraActionCooldownSeconds,
                config.fusion.danceDurationSeconds, config.fusion.danceCooldownSeconds);
    }

    public static IntegrationConfig config() {
        IntegrationConfig value = config;
        if (value == null) throw new IllegalStateException("SVFrameMMO: Cobblemon Integration is not initialized");
        return value;
    }

    public static PotaraTierResolver potara() { return POTARA; }
    public static FusionService fusions() { return FUSIONS; }
    public static CobblemonMoveVfxService moveVfx() { return MOVE_VFX; }
}
