package vn.svframe.fantasyhub.client.mixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.client.FantasyShopScreen;
/** Keep queued tutorial toasts out of purchase controls; other screens keep vanilla behavior. */
@Mixin(ToastManager.class)
public abstract class ShopToastMixin {
 @Inject(method="draw",at=@At("HEAD"),cancellable=true)
 private void fantasyhub$shopToast(DrawContext context,CallbackInfo ci){if(MinecraftClient.getInstance().currentScreen instanceof FantasyShopScreen)ci.cancel();}
}
