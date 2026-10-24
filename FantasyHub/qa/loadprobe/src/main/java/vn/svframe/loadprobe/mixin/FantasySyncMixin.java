package vn.svframe.loadprobe.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;import vn.svframe.loadprobe.FantasyProbe;
@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets="vn.svframe.fantasyhub.core.NativeSyncRuntime",remap=false) public abstract class FantasySyncMixin {
 @Inject(method="tick",at=@At("HEAD"),remap=false)private void qa$begin(CallbackInfo ci){FantasyProbe.begin();}
 @Inject(method="tick",at=@At("RETURN"),remap=false)private void qa$end(CallbackInfo ci){FantasyProbe.end();}
}
