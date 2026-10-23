package vn.svframe.svframemmo.cobblemon.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonServerDelegate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Encounter HP belongs to the native living entity, rather than the battle stat curve. */
@Mixin(PokemonServerDelegate.class)
public abstract class PokemonBossHealthMixin {
  @Shadow public abstract PokemonEntity getEntity();

  @Redirect(method = "updateAttributes", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/entity/attribute/EntityAttributeInstance;setBaseValue(D)V"))
  private void svframe$preserveEncounterMaximum(EntityAttributeInstance attribute, double value) {
    var entity = getEntity();
    if (entity.getCommandTags().contains("svframe_worldboss")
        && attribute == entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)) return;
    attribute.setBaseValue(value);
  }

  @Inject(method = "updateHealth", at = @At("HEAD"), cancellable = true)
  private void svframe$preserveEncounterDamage(Pokemon pokemon, CallbackInfo ci) {
    if (getEntity().getCommandTags().contains("svframe_worldboss")||vn.svframe.svframemmo.cobblemon.gameplay.WildRpgHealth.active(getEntity())) ci.cancel();
  }
}
