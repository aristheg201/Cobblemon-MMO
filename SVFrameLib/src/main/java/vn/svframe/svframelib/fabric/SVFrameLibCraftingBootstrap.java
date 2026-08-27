package vn.svframe.svframelib.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/** Native Fabric bootstrap for the complete SVFrameLib crafting station surface. */
public final class SVFrameLibCraftingBootstrap implements ModInitializer {
    @Override public void onInitialize() {
        SVFrameLibVanillaCraftingMod.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SVFrameLibWorkbenchCommands.register(dispatcher));
    }
}
