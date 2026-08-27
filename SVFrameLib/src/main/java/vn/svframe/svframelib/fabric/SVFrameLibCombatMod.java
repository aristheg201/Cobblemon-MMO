package vn.svframe.svframelib.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public final class SVFrameLibCombatMod implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricDamageBridge.reload();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("svframelibfabric")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("combat")
                                .then(literal("status").executes(ctx -> {
                                    ctx.getSource().sendFeedback(() -> Text.literal("SVFrameLib combat | " + FabricDamageBridge.summary()), false);
                                    return 1;
                                }))
                                .then(literal("reload").executes(ctx -> {
                                    boolean ok = FabricDamageBridge.reload();
                                    if (!ok) {
                                        ctx.getSource().sendError(Text.literal("SVFrameLib combat settings reload failed."));
                                        return 0;
                                    }
                                    ctx.getSource().sendFeedback(() -> Text.literal("SVFrameLib combat reloaded | " + FabricDamageBridge.summary()), true);
                                    return 1;
                                })))));
    }
}
