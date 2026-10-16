package vn.svframe.svframeitems.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframeitems.runtime.ThermalTuningRuntime;

/** TAN normally checks only chest-slot enchantments. Include actual RPG/accessory equipment. */
@Pseudo
@Mixin(targets="toughasnails.temperature.TemperatureHelperImpl", remap=false)
abstract class ThermalTuningMixin {
    @Inject(method="armorModifier", at=@At("RETURN"), cancellable=true, remap=false)
    private static void svframe$thermal(PlayerEntity player, @Coerce Object current, CallbackInfoReturnable<Object> ci) {
        if (!ThermalTuningRuntime.protectedPlayer(player)) return;
        try { ci.setReturnValue(current.getClass().getField("NEUTRAL").get(null)); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unsupported Tough As Nails temperature API",failure); }
    }
}
