package vn.svframe.svframemmo.mixin;

import com.mojang.brigadier.ParseResults;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.runtime.CommandEventRuntime;

/** Cancels native RPG commands before Brigadier execution when a public command listener denies them. */
@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private void svframemmo$commandEvent(ParseResults<ServerCommandSource> parseResults, String command, CallbackInfo ci) {
        if (parseResults != null && CommandEventRuntime.cancel(parseResults.getContext().getSource(), command)) ci.cancel();
    }
}
