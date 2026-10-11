package vn.svframe.skript.fabric.mixin;
import java.util.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import vn.svframe.skript.fabric.*;
@Mixin(LivingEntity.class)
public abstract class DamageMixin {
 @WrapMethod(method="damage")
 private boolean svframe$damage(DamageSource source,float amount,Operation<Boolean> original){
  LivingEntity victim=(LivingEntity)(Object)this;if(victim.getWorld().isClient)return original.call(source,amount);
  Map<String,Object> event=victim instanceof ServerPlayerEntity player?SVFrameSkript.playerEvent(player):new HashMap<>();
  event.put("victim",FabricPlatform.ref(victim));event.put("entity",FabricPlatform.ref(victim));event.put("damage",(double)amount/2);
  if(source.getAttacker()!=null)event.put("attacker",FabricPlatform.ref(source.getAttacker()));
  if(victim instanceof ServerPlayerEntity){Map<String,Object> playerState=SVFrameSkript.dispatch("damage of player",event);if(Boolean.TRUE.equals(playerState.get("cancelled")))return false;event.put("damage",playerState.getOrDefault("damage",(double)amount/2));}
  Map<String,Object> state=SVFrameSkript.dispatch("damage",event);if(Boolean.TRUE.equals(state.get("cancelled")))return false;
  double damage=((Number)state.getOrDefault("damage",(double)amount/2)).doubleValue()*2;
  if(!Double.isFinite(damage)||damage<0||damage>Float.MAX_VALUE)throw new IllegalArgumentException("Invalid Skript damage");
  return original.call(source,(float)damage);
 }
}
