package dev.aristheg.alphaencounter;

import dev.aristheg.alphaencounter.command.AdminCommands;
import dev.aristheg.alphaencounter.config.AlphaEncounterConfigManager;
import dev.aristheg.alphaencounter.config.UiConfigManager;
import dev.aristheg.alphaencounter.integration.AlphaLootGuard;
import dev.aristheg.alphaencounter.runtime.EncounterRuntime;
import dev.aristheg.alphaencounter.text.TextService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AlphaEncounterMod implements ModInitializer {
    public static final String MOD_ID = "alpha_encounter";
    public static final Logger LOGGER = LoggerFactory.getLogger("Alpha-Encounter");
    public static final AlphaEncounterConfigManager CONFIG = new AlphaEncounterConfigManager();
    public static final UiConfigManager UI = new UiConfigManager(CONFIG);
    public static final EncounterRuntime RUNTIME = new EncounterRuntime(CONFIG);
    public static final TextService TEXT = new TextService(CONFIG);

    @Override
    public void onInitialize() {
        CONFIG.load();
        UI.load();
        RUNTIME.initialize();
        AlphaLootGuard.initialize();
        TEXT.initialize(RUNTIME);

        ServerLifecycleEvents.SERVER_STARTING.register(TEXT::start);
        ServerTickEvents.END_SERVER_TICK.register(RUNTIME::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(RUNTIME::saveState);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TEXT.stop());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AdminCommands.register(dispatcher));

        LOGGER.info("Alpha-Encounter initialized: shared HP, MiniMessage, optional Placeholder API, vanilla field combat, runtime-safe IV-candy guard.");
    }
}
