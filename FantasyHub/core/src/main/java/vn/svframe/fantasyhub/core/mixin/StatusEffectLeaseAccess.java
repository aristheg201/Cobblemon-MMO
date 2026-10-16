package vn.svframe.fantasyhub.core.mixin;

import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Release only the belt's hidden lease when a stronger vanilla potion temporarily replaces it. */
@Mixin(StatusEffectInstance.class)
public interface StatusEffectLeaseAccess {
  @Accessor("hiddenEffect") StatusEffectInstance fantasyhub$hidden();
  @Accessor("hiddenEffect") void fantasyhub$hidden(StatusEffectInstance value);
}
