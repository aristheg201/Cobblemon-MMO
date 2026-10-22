package vn.svframe.svframemmo.cobblemon.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframemmo.cobblemon.gameplay.PokemonHostility;

/** Claim the brain phase, preserving native navigation/move controls run by MobEntity. */
@Mixin(PokemonEntity.class)
public abstract class PokemonHostilityMixin {
  @Inject(method = "isUncatchable", at = @At("HEAD"), cancellable = true)
  private void svframe$bossUncatchable(CallbackInfoReturnable<Boolean> cir) {
    if (((PokemonEntity) (Object) this).getCommandTags().stream().anyMatch(t->t.equals("svframe_worldboss")||t.equals("svframe_cosmic_minion")))
      cir.setReturnValue(true);
  }

  @Inject(method = "canBattle", at = @At("HEAD"), cancellable = true)
  private void svframe$bossNoBattle(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
    if (((PokemonEntity) (Object) this).getCommandTags().stream().anyMatch(t->t.equals("svframe_worldboss")||t.equals("svframe_cosmic_minion")))
      cir.setReturnValue(false);
  }

  @Inject(method = "forceBattle", at = @At("HEAD"), cancellable = true)
  private void svframe$bossNoForceBattle(
      ServerPlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
    if (((PokemonEntity) (Object) this).getCommandTags().stream().anyMatch(t->t.equals("svframe_worldboss")||t.equals("svframe_cosmic_minion")))
      cir.setReturnValue(false);
  }

  @Inject(method = "mobTick", at = @At("HEAD"), cancellable = true)
  private void svframe$hostility(CallbackInfo ci) {
    if (PokemonHostility.tick((PokemonEntity) (Object) this)) ci.cancel();
  }
}
