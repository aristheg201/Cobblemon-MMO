package vn.svframe.fantasyhub.core.mixin;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.core.hero.HeroRuntime;
/** Preserve native Elytra flag only during our server-owned Back-cape lease; no chest durability. */
@Mixin(LivingEntity.class)
public abstract class BatmanGlideMixin {
 @Inject(method="tickFallFlying",at=@At("HEAD"),cancellable=true)
 private void fantasyhub$cape(CallbackInfo ci){if((Object)this instanceof ServerPlayerEntity p&&HeroRuntime.batmanGliding(p))ci.cancel();}
}
