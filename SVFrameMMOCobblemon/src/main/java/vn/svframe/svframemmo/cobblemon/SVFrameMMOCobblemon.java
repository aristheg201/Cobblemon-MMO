package vn.svframe.svframemmo.cobblemon;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.moves.Moves;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.svframelib.api.event.skill.PlayerCastSkillEvent;
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
import vn.svframe.svframemmo.cobblemon.fusion.FusionVisualBridge;
import vn.svframe.svframemmo.cobblemon.fusion.PotaraCommands;
import vn.svframe.svframemmo.cobblemon.fusion.PotaraUseHandler;
import vn.svframe.svframemmo.cobblemon.integration.CobblemonMoveVfxService;
import vn.svframe.svframemmo.cobblemon.integration.LuckPermsIntegration;
import vn.svframe.svframemmo.cobblemon.integration.PlaceholderIntegration;
import vn.svframe.svframemmo.cobblemon.item.PotaraTierResolver;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveConfigGenerator;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkill;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkillAdapter;
import vn.svframe.svframemmo.cobblemon.move.PokemonSkillCommands;
import vn.svframe.svframemmo.cobblemon.move.PokemonSkillIconResolver;
import vn.svframe.svframemmo.cobblemon.move.PokemonSkillShopService;

/** Separate native integration mod bridging Cobblemon and Mega Showdown to SVFrameMMO. */
public final class SVFrameMMOCobblemon implements ModInitializer {
    public static final String ID = "svframemmo_cobblemon";
    public static final Logger LOG = LoggerFactory.getLogger("SVFrameMMO: Cobblemon Integration");
    private static final long FUSION_RUNTIME_INTERVAL_TICKS = 2L;
    private static volatile IntegrationConfig config;
    private static final PotaraTierResolver POTARA = new PotaraTierResolver();
    private static final FusionService FUSIONS = new FusionService();
    private static final CobblemonMoveVfxService MOVE_VFX = new CobblemonMoveVfxService();
    private static final CosmeticService COSMETICS = new CosmeticService();
    private static final PokemonSkillShopService POKEMON_SKILLS = new PokemonSkillShopService();

    @Override
    public void onInitialize() {
        try {
            config = IntegrationConfig.load();
        } catch (Exception error) {
            throw new IllegalStateException("Could not load SVFrameMMO Cobblemon integration config", error);
        }

        try {
            CosmeticDefaults.ensure();
            COSMETICS.reloadDefinitions();
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize Cobblemon Integration cosmetic definitions", error);
        }

        SnowstormPackService.install();
        CobblemonMoveSkillAdapter.registerSkillSource();
        LuckPermsIntegration.initialize();
        PlaceholderIntegration.registerIfPresent();
        if (Moves.count() > 0) synchronizeMoveProviderOrThrow();

        CobblemonEvents.COBBLEMON_INITIALISED.subscribe(ignored -> {
            synchronizeMoveProviderOrThrow();
            if (!SVFrameMMO.reload())
                LOG.warn("SVFrameMMO reload after Cobblemon move registry initialization failed");
        });
        Moves.INSTANCE.getObservable().subscribe(ignored -> synchronizeMoveProviderOrThrow());

        FusionLockHooks.register(FUSIONS);
        PotaraUseHandler.register(FUSIONS);

        // Player cosmetics are deliberately independent from skills. This event is only for real move VFX/Fusion.
        PlayerCastSkillEvent.EVENT.register(event -> {
            if (!event.isCancelled() && event.getResult() != null
                    && event.getResult().isSuccessful(event.getMetadata())
                    && event.getCast().getHandler() instanceof CobblemonMoveSkill move) {
                MOVE_VFX.renderActor(event.getPlayer(), move.template());
                FusionVisualBridge.playMoveAnimation(event.getPlayer(), move.template());
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FusionCommands.register(dispatcher, FUSIONS);
            PotaraCommands.register(dispatcher);
            PokemonSkillCommands.register(dispatcher, POKEMON_SKILLS);
            CosmeticCommands.register(dispatcher, COSMETICS);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !FUSIONS.blocksDamage(entity));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (blocked || damageTaken <= 0.0F) return;
            if (source.getAttacker() instanceof ServerPlayerEntity attacker && source.getSource() == attacker
                    && !FUSIONS.isExecutingMoveDamage(attacker))
                FusionVisualBridge.playSuccessfulBasicAttack(attacker);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getAttacker() instanceof ServerPlayerEntity attacker && source.getSource() == attacker
                    && !FUSIONS.isExecutingMoveDamage(attacker))
                FusionVisualBridge.playSuccessfulBasicAttack(attacker);
        });

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
            verifyPotaraCommand(server);
            FUSIONS.cooldowns().start(server);
            COSMETICS.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            FUSIONS.cooldowns().stop();
            COSMETICS.stop();
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long tick = SVFrameMMO.currentTick();
            if (tick % FUSION_RUNTIME_INTERVAL_TICKS == 0L) FUSIONS.tick(tick, server);
            FUSIONS.cooldowns().tick(tick);
            COSMETICS.tick(tick, server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> COSMETICS.onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            FUSIONS.onDisconnect(handler.player);
            COSMETICS.onDisconnect(handler.player);
        }));

        LOG.info("Cobblemon Integration online; fusionVisual=server-packet-stand/native-1.21.1, provider=cobblemon, providerMoves={}, registeredSkills={}, maxSkillLevel={}, specificMoveVfx={}, genericMoveVfx={}, cosmetics={}, PokemonSkillShop={}/{}, Potara cooldown={}s, Fusion Dance={}s/{}s cooldown",
                CobblemonMoveSkillAdapter.providerSize(),
                SVFrameMMO.externalSkills().getByOwner(CobblemonMoveSkillAdapter.REGISTRY_OWNER).size(),
                config.pokemonSkills.maxLevel,
                MOVE_VFX.planCount(), MOVE_VFX.genericPlanCount(), COSMETICS.size(),
                config.pokemonSkills.enabled, config.pokemonSkills.normalizedProvider(),
                config.fusion.potaraActionCooldownSeconds,
                config.fusion.danceDurationSeconds,
                config.fusion.danceCooldownSeconds);
    }

    private static synchronized void synchronizeMoveProviderOrThrow() {
        MOVE_VFX.reload();
        CobblemonMoveSkillAdapter.reload();
        int provider = CobblemonMoveSkillAdapter.providerSize();
        int generated = CobblemonMoveSkillAdapter.size();
        int registered = SVFrameMMO.externalSkills()
                .getByOwner(CobblemonMoveSkillAdapter.REGISTRY_OWNER).size();
        int configs;
        try {
            configs = CobblemonMoveConfigGenerator.regenerate(MOVE_VFX);
        } catch (Exception error) {
            throw new IllegalStateException("Could not generate Cobblemon provider move configs", error);
        }
        if (provider <= 0)
            throw new IllegalStateException("Cobblemon move provider is loaded but exposes zero moves");
        if (generated != provider || registered != provider || configs != provider)
            throw new IllegalStateException("Cobblemon provider synchronization mismatch: provider=" + provider
                    + ", generated=" + generated + ", registered=" + registered + ", configs=" + configs);

        long megaIcons = Moves.all().stream()
                .filter(move -> "mega_showdown".equals(PokemonSkillIconResolver.source(move))).count();
        long cobblemonIcons = Moves.all().stream()
                .filter(move -> "cobblemon".equals(PokemonSkillIconResolver.source(move))).count();
        long fallbackIcons = provider - megaIcons - cobblemonIcons;
        LOG.info("Pokemon skill icons resolved; megaShowdown={}, cobblemon={}, fallback={}",
                megaIcons, cobblemonIcons, fallbackIcons);
        LOG.info("Cobblemon move provider synchronized; providerMoves={}, registeredSkills={}, generatedConfigs={}, specificMoveVfx={}, genericMoveVfx={}",
                provider, registered, configs, MOVE_VFX.planCount(), MOVE_VFX.genericPlanCount());
    }

    private static void verifyPotaraCommand(net.minecraft.server.MinecraftServer server) {
        var root = server.getCommandManager().getDispatcher().getRoot().getChild("potara");
        if (root == null || root.getChild("give") == null)
            throw new IllegalStateException("Potara command registration failed: expected /potara give");
        LOG.info("Potara admin command registered: /potara give <tier> [player] [amount]; LuckPerms={}",
                LuckPermsIntegration.POTARA_GIVE);
    }

    public static IntegrationConfig config() {
        IntegrationConfig value = config;
        if (value == null)
            throw new IllegalStateException("SVFrameMMO: Cobblemon Integration is not initialized");
        return value;
    }

    public static PotaraTierResolver potara() { return POTARA; }
    public static FusionService fusions() { return FUSIONS; }
    public static CobblemonMoveVfxService moveVfx() { return MOVE_VFX; }
    public static CosmeticService cosmetics() { return COSMETICS; }
    public static PokemonSkillShopService pokemonSkills() { return POKEMON_SKILLS; }
}
