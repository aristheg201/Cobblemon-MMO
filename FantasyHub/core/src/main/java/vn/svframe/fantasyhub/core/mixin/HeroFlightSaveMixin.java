package vn.svframe.fantasyhub.core.mixin;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.core.hero.HeroRuntime;

/** Temporary class flight is never persisted as permanent vanilla flight permission. */
@Mixin(ServerPlayerEntity.class)
abstract class HeroFlightSaveMixin {
  @Inject(method="writeCustomDataToNbt",at=@At("TAIL"))
  private void fantasyhub$flightBaseline(NbtCompound nbt,CallbackInfo ci){HeroRuntime.saveFlightBaseline((ServerPlayerEntity)(Object)this,nbt);}
}
