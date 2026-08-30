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
import vn.svframe.svframelib.api.event.skill.SkillCastEvent;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.config.IntegrationConfig;
import vn.svframe.svframemmo.cobblemon.cosmetic.CosmeticCommands;
import vn.svframe.svframemmo.cobblemon.cosmetic.CosmeticDefaults;
import vn.svframe.svframemmo.cobblemon.cosmetic.CosmeticService;
import vn.svframe.svframemmo.cobblemon.cosmetic.SnowstormAssetLoader;
import vn.svframe.svframemmo.cobblemon.cosmetic.SnowstormPackService;
import vn.svframe.svframemmo.cobblemon.fusion.FusionCommands;
import vn.svframe.svframemmo.cobblemon.fusion.FusionLockHooks;
import vn.svframe.svframemmo.cobblemon.fusion.FusionNetworkGuards;
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;
import vn.svframe.svframemmo.cobblemon.fusion.PotaraUseHandler;
import vn.svframe.svframemmo.cobblemon.fusion.render.FusionMorphNetworking;
import vn.svframe.svframemmo.cobblemon.integration.CobblemonMoveVfxService;
import vn.svframe.svframemmo.cobblemon.integration.LuckPermsIntegration;
import vn.svframe.svframemmo.cobblemon.integration.PlaceholderIntegration;
import vn.svframe.svframemmo.cobblemon.item.PotaraTierResolver;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkill;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkillAdapter;
import vn.svframe.svframemmo.cobblemon.move.PokemonSkillCommands;
import vn.svframe.svframemmo.cobblemon.move.PokemonSkillShopService;

/** Separate native integration mod bridging Cobblemon and Mega Showdown to SVFrameMMO. */
public final class SVFrameMMOCobblemon implements ModInitializer {
    public static final String ID = "svframemmo_cobblemon";
    public static final Logger LOG = LoggerFactory.getLogger("SVFrameMMO: Cobblemon Integration");
    private static volatile IntegrationConfig config;
    private static final PotaraTierResolver POTARA = new PotaraTierResolver();
    private static final FusionService FUSIONS = new FusionService();
    private static final CobblemonMoveVfxService MOVE_VFX = new CobblemonMoveVfxService();
    private static final CosmeticService COSMETICS = new CosmeticService();
    private static final PokemonSkillShopService POKEMON_SKILLS = new PokemonSkillShopService();

    @Override public void onInitialize() {
        FusionMorphNetworking.register();
        try { config = IntegrationConfig.load(); }
        catch (Exception error) { throw new IllegalStateException("Could not load SVFrameMMO Cobblemon integration config", error); }

        try {
            CosmeticDefaults.ensure();
            COSMETICS.reloadDefinitions();
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize Cobblemon Integration cosmetic definitions", error);
        }

        SnowstormPackService.install();
        CobblemonMoveSkillAdapter.registerSkillSource();
        MOVE_VFX.reload();
        LuckPermsIntegration.initialize();
        PlaceholderIntegration.registerIfPresent();
        if (Moves.count() > 0) reloadMoveSkillsOrThrow();
        CobblemonEvents.COBBLEMON_INITIALISED.subscribe(ignored -> {
            reloadMoveSkillsOrThrow();
            MOVE_VFX.reload();
            if (!SVFrameMMO.reload()) LOG.warn("SVFrameMMO reload after Cobblemon move registry initialization failed");
        });
        Moves.INSTANCE.getObservable().subscribe(ignored -> reloadMoveSkillsOrThrow());

        FusionLockHooks.register(FUSIONS);
        PotaraUseHandler.register(FUSIONS);
        PlayerCastSkillEvent.EVENT.register(event -> {
            COSMETICS.onSkillStart(event);
            if (!event.isCancelled() && event.getResult() != null && event.getResult().isSuccessful(event.getMetadata())
                    && event.getCast().getHandler() instanceof CobblemonMoveSkill move)
                MOVE_VFX.renderActor(event.getPlayer(), move.template());
        });
        SkillCastEvent.EVENT.register(COSMETICS::onSkillSuccess);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FusionCommands.register(dispatcher, FUSIONS);
            PokemonSkillCommands.register(dispatcher, POKEMON_SKILLS);
            CosmeticCommands.register(dispatcher, COSMETICS);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !FUSIONS.blocksDamage(entity));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            FusionNetworkGuards.register(FUSIONS);
            try {
                SnowstormPackService.stage(new SnowstormAssetLoader().load(CosmeticDefaults.VFX));
                SnowstormPackService.buildInitial();
            } catch (Exception error) {
                LOG.warn("Could not stage cosmetic Snowstorm assets; vanilla fallbacks remain available", error);
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FUSIONS.cooldowns().start(server);
            COSMETICS.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            FUSIONS.cooldowns().stop();
            COSMETICS.stop();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long tick = SVFrameMMO.currentTick();
            FUSIONS.tick(tick, server);
            FUSIONS.cooldowns().tick(tick);
            COSMETICS.tick(tick, server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> COSMETICS.onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            server.execute(() -> {
                FUSIONS.onDisconnect(handler.player);
                COSMETICS.onDisconnect(handler.player);
            });
        });
        LOG.info("Cobblemon Integration online; generatedMoves={}, registeredSkills={}, moveVfxPlans={}, cosmetics={}, PokemonSkillShop={}/{}, Potara cooldown={}s, Fusion Dance={}s/{}s cooldown",
                CobblemonMoveSkillAdapter.size(),
                SVFrameMMO.externalSkills().getByOwner(CobblemonMoveSkillAdapter.REGISTRY_OWNER).size(),
                MOVE_VFX.planCount(), COSMETICS.size(), config.pokemonSkills.enabled, config.pokemonSkills.normalizedProvider(),
                config.fusion.potaraActionCooldownSeconds, config.fusion.danceDurationSeconds, config.fusion.danceCooldownSeconds);
    }

    private static void reloadMoveSkillsOrThrow() {
        CobblemonMoveSkillAdapter.reload();
        int generated = CobblemonMoveSkillAdapter.size();
        int registered = SVFrameMMO.externalSkills().getByOwner(CobblemonMoveSkillAdapter.REGISTRY_OWNER).size();
        if (generated <= 0)
            throw new IllegalStateException("Cobblemon move catalog is loaded but zero SVFrameMMO Pokemon skills were generated");
        if (registered != generated)
            throw new IllegalStateException("Cobblemon move registration mismatch: generated=" + generated + ", registered=" + registered);
        LOG.info("Cobblemon move skills published; generatedMoves={}, registeredSkills={}", generated, registered);
    }

    public static IntegrationConfig config() {
        IntegrationConfig value = config;
        if (value == null) throw new IllegalStateException("SVFrameMMO: Cobblemon Integration is not initialized");
        return value;
    }

    public static PotaraTierResolver potara() { return POTARA; }
    public static FusionService fusions() { return FUSIONS; }
    public static CobblemonMoveVfxService moveVfx() { return MOVE_VFX; }
    public static CosmeticService cosmetics() { return COSMETICS; }
    public static PokemonSkillShopService pokemonSkills() { return POKEMON_SKILLS; }
}
