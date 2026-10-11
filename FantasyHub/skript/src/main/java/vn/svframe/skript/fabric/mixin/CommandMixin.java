package vn.svframe.skript.fabric.mixin;
import com.mojang.brigadier.ParseResults;
import net.minecraft.server.command.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.skript.fabric.SVFrameSkript;
@Mixin(CommandManager.class)
public abstract class CommandMixin {
 @Inject(method="execute",at=@At("HEAD"),cancellable=true)
 private void svframe$command(ParseResults<ServerCommandSource> parsed,String command,CallbackInfo ci){if(SVFrameSkript.command(parsed.getContext().getSource(),command))ci.cancel();}
}
