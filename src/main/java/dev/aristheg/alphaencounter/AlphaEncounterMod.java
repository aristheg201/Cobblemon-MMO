package dev.aristheg.alphaencounter;

import dev.aristheg.alphaencounter.command.AdminCommands;
import dev.aristheg.alphaencounter.config.AlphaEncounterConfigManager;
import dev.aristheg.alphaencounter.runtime.EncounterRuntime;
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
    public static final EncounterRuntime RUNTIME = new EncounterRuntime(CONFIG);

    @Override
    public void onInitialize() {
        CONFIG.load();
        RUNTIME.initialize();
        ServerTickEvents.END_SERVER_TICK.register(RUNTIME::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(RUNTIME::saveState);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AdminCommands.register(dispatcher));
        LOGGER.info("Alpha-Encounter initialized: Cobblemon-native battle events, vanilla field attacks, modular config tree, no SVFrame dependency.");
    }
}
