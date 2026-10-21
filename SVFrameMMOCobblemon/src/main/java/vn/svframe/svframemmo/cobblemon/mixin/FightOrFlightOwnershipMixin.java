package vn.svframe.svframemmo.cobblemon.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframemmo.cobblemon.gameplay.PokemonHostility;

/** FoF 0.11.0/0.11.1 public entrypoints: claimed wild Pokémon have exactly one combat executor. */
@Pseudo
@Mixin(targets = "me.rufia.fightorflight.entity.PokemonAttackEffect", remap = false)
public abstract class FightOrFlightOwnershipMixin {
  @Unique
  private static boolean svframe$ownsCombat(PokemonEntity pokemon) {
    return pokemon.getCommandTags().contains("svframe_worldboss")
        || PokemonHostility.claims(pokemon);
  }

  @Inject(method = "pokemonAttack", at = @At("HEAD"), cancellable = true, remap = false)
  private static void svframe$melee(
      PokemonEntity pokemon, Entity target, CallbackInfoReturnable<Boolean> cir) {
    if (svframe$ownsCombat(pokemon)) cir.setReturnValue(false);
  }

  @Inject(
      method = "pokemonPerformRangedAttack",
      at = @At("HEAD"),
      cancellable = true,
      remap = false)
  private static void svframe$ranged(PokemonEntity pokemon, LivingEntity target, CallbackInfo ci) {
    if (svframe$ownsCombat(pokemon)) ci.cancel();
  }

  @Inject(method = "canUseMove", at = @At("HEAD"), cancellable = true, remap = false)
  private static void svframe$move(PokemonEntity pokemon, CallbackInfoReturnable<Boolean> cir) {
    if (svframe$ownsCombat(pokemon)) cir.setReturnValue(false);
  }
}
