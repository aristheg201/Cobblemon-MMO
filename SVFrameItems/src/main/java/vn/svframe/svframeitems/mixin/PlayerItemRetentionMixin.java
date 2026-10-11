package vn.svframe.svframeitems.mixin;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframeitems.runtime.RetainedItems;
import java.util.*;
@Mixin(PlayerEntity.class)
public abstract class PlayerItemRetentionMixin implements RetainedItems {
    @Unique private final Map<Integer,ItemStack> svframe$retained=new LinkedHashMap<>();
    public Map<Integer,ItemStack> svframe$retained(){return svframe$retained;}
    @Inject(method="writeCustomDataToNbt",at=@At("RETURN"))
    private void svframe$save(NbtCompound nbt,CallbackInfo ci){var player=(PlayerEntity)(Object)this;NbtList items=new NbtList();svframe$retained.forEach((slot,stack)->{NbtCompound row=new NbtCompound();row.putInt("slot",slot);row.put("stack",stack.encode(player.getRegistryManager()));items.add(row);});nbt.put("SVFrameItemsRetained",items);}
    @Inject(method="readCustomDataFromNbt",at=@At("RETURN"))
    private void svframe$load(NbtCompound nbt,CallbackInfo ci){var player=(PlayerEntity)(Object)this;svframe$retained.clear();var items=nbt.getList("SVFrameItemsRetained",NbtElement.COMPOUND_TYPE);for(int i=0;i<items.size();i++){var row=items.getCompound(i);int slot=row.getInt("slot");if(slot<-1024||slot>=player.getInventory().size())continue;ItemStack.fromNbt(player.getRegistryManager(),row.getCompound("stack")).ifPresent(stack->svframe$retained.put(slot,stack));}}
}
