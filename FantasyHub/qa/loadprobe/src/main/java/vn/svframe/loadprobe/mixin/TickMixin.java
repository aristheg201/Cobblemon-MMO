package vn.svframe.loadprobe.mixin;
import vn.svframe.loadprobe.LoadProbe;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;import net.minecraft.server.MinecraftServer;
@Mixin(MinecraftServer.class) public abstract class TickMixin {
 @Inject(method="tick",at=@At("HEAD")) private void qa$begin(CallbackInfo ci){LoadProbe.begin();}
 @Inject(method="tick",at=@At("RETURN")) private void qa$end(CallbackInfo ci){LoadProbe.end();}
}
