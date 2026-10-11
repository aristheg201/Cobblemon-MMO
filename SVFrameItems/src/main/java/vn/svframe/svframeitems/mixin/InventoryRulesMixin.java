package vn.svframe.svframeitems.mixin;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import vn.svframe.svframeitems.runtime.*;
@Mixin(PlayerInventory.class)
public abstract class InventoryRulesMixin {
    @Shadow @Final public PlayerEntity player;
    @Inject(method="dropSelectedItem",at=@At("HEAD"),cancellable=true)
    private void svframe$manualDrop(boolean entireStack,CallbackInfoReturnable<ItemStack> ci){if(SourceItemRules.flag(player.getMainHandStack(),"disable-droping"))ci.setReturnValue(ItemStack.EMPTY);}
    @Inject(method="dropAll",at=@At("HEAD"))
    private void svframe$retain(CallbackInfo ci){if(!(player instanceof ServerPlayerEntity))return;var inventory=(PlayerInventory)(Object)this;var saved=((RetainedItems)player).svframe$retained();for(int slot=0;slot<inventory.size();slot++){ItemStack stack=inventory.getStack(slot);if(SourceItemRules.flag(stack,"disable-death-drop")){int key=slot;while(saved.containsKey(key))key=key>=0?-1:key-1;saved.put(key,stack.copy());inventory.setStack(slot,ItemStack.EMPTY);}}}
}
