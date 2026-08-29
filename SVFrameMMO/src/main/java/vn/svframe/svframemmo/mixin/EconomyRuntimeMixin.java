package vn.svframe.svframemmo.mixin;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframemmo.economy.EconomyParityRuntime;
import vn.svframe.svframemmo.economy.EconomyRuntime;
import vn.svframe.svframemmo.manager.ConfigItemManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/** Connects configurable items and withdraw parity to the already validated economy runtime lifecycle. */
@Mixin(value = EconomyRuntime.class, remap = false)
public abstract class EconomyRuntimeMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private void svframemmo$reloadConfigItems(CallbackInfo ci) {
        try {
            ConfigItemManager.instance().reload();
            ConfigItemManager.instance().getOrThrow("GOLD_COIN");
            ConfigItemManager.instance().getOrThrow("NOTE");
            ConfigItemManager.instance().getOrThrow("DEPOSIT_ITEM");
            ConfigItemManager.instance().getOrThrow("GOLD_POUCH");
            ConfigItemManager.instance().getOrThrow("MOB_GOLD_POUCH");
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load native economy item templates", exception);
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void svframemmo$installWithdrawParity(CallbackInfo ci) {
        EconomyParityRuntime.instance().install();
    }

    @Inject(method = "depositButton", at = @At("HEAD"), cancellable = true)
    private static void svframemmo$configuredDepositButton(BigDecimal worth, CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(ConfigItemManager.instance().build("DEPOSIT_ITEM",
                Map.of("worth", worth.stripTrailingZeros().toPlainString())));
    }
}
