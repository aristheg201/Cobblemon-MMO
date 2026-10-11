package vn.svframe.svframeitems.mixin;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframeitems.runtime.SourceItemRules;
@Mixin(ScreenHandler.class)
public abstract class ScreenItemRulesMixin {
    @Inject(method="onSlotClick",at=@At("HEAD"),cancellable=true)
    private void svframe$drop(int slot,int button,SlotActionType action,PlayerEntity player,CallbackInfo ci){var screen=(ScreenHandler)(Object)this;if(slot==-999&&SourceItemRules.flag(screen.getCursorStack(),"disable-droping"))ci.cancel();else if(action==SlotActionType.THROW&&slot>=0&&slot<screen.slots.size()&&SourceItemRules.flag(screen.getSlot(slot).getStack(),"disable-droping"))ci.cancel();}
}
