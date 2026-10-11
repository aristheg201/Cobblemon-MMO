package vn.svframe.skript.fabric.mixin;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.skript.fabric.SkriptDeathState;
@Mixin(PlayerEntity.class)
public abstract class DeathStateMixin implements SkriptDeathState {
 @Unique private boolean svframe$keep,svframe$drop;
 @Unique private String svframe$message;
 public boolean svframe$keepInventory(){return svframe$keep;}public void svframe$keepInventory(boolean value){svframe$keep=value;}
 public boolean svframe$cancelDrops(){return svframe$drop;}public void svframe$cancelDrops(boolean value){svframe$drop=value;}
 public String svframe$deathMessage(){return svframe$message;}public void svframe$deathMessage(String value){svframe$message=value;}
 @Inject(method="writeCustomDataToNbt",at=@At("RETURN"))
 private void svframe$save(NbtCompound nbt,CallbackInfo ci){nbt.putBoolean("SVFrameSkriptKeepInventory",svframe$keep);}
 @Inject(method="readCustomDataFromNbt",at=@At("RETURN"))
 private void svframe$load(NbtCompound nbt,CallbackInfo ci){svframe$keep=nbt.getBoolean("SVFrameSkriptKeepInventory");}
}
