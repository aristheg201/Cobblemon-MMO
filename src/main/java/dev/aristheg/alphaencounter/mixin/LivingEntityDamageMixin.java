package dev.aristheg.alphaencounter.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.aristheg.alphaencounter.AlphaEncounterMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
    /**
     * Redirect only the final health mutation inside LivingEntity.applyDamage.
     * This preserves vanilla/modded damage calculation before this point while
     * preventing managed field Pokemon from losing their raw entity health.
     */
    @Redirect(
        method = "applyDamage",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;setHealth(F)V")
    )
    private void alphaEncounter$redirectResolvedHealth(
        LivingEntity target,
        float newHealth,
        DamageSource source,
        float originalAmount
    ) {
        if (target instanceof PokemonEntity pokemon && AlphaEncounterMod.RUNTIME.shouldRedirectDamage(pokemon)) {
            float resolvedDamage = Math.max(0.0f, target.getHealth() - newHealth);
            AlphaEncounterMod.RUNTIME.onResolvedWorldDamage(pokemon, source, resolvedDamage);
            return;
        }
        target.setHealth(newHealth);
    }
}
