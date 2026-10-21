package vn.svframe.fantasyhub.protocol;
import net.minecraft.item.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.*;
/** The common item delegates use to the server owner without depending on core classes. */
public final class GrapplingHookItem extends Item {
 public interface Handler {boolean use(PlayerEntity player);}
 private static Handler handler=p->false;
 public static void handler(Handler value){handler=value;}
 public GrapplingHookItem(Settings settings){super(settings);}
 @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){
  var stack=player.getStackInHand(hand);if(hand!=Hand.MAIN_HAND)return TypedActionResult.pass(stack);
  if(world.isClient||handler.use(player)){player.setCurrentHand(hand);return TypedActionResult.consume(stack);}return TypedActionResult.fail(stack);
 }
 @Override public int getMaxUseTime(ItemStack stack,net.minecraft.entity.LivingEntity user){return 72000;}
 @Override public UseAction getUseAction(ItemStack stack){return UseAction.BOW;}
}
