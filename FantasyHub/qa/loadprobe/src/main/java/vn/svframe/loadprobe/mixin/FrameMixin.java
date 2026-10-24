package vn.svframe.loadprobe.mixin;
import net.minecraft.client.MinecraftClient;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;import vn.svframe.loadprobe.FrameProbe;
@Mixin(MinecraftClient.class) public abstract class FrameMixin {
 @Inject(method="render",at=@At("HEAD"))private void qa$begin(boolean tick,CallbackInfo ci){FrameProbe.begin();}
 @Inject(method="render",at=@At("RETURN"))private void qa$end(boolean tick,CallbackInfo ci){FrameProbe.end();}
}
