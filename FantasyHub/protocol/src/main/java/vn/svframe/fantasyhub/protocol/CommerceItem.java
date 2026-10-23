package vn.svframe.fantasyhub.protocol;
import net.minecraft.item.*;import net.minecraft.entity.player.PlayerEntity;import net.minecraft.util.*;import net.minecraft.world.World;
/** Common item contract; server modules install handlers without client class references. */
public final class CommerceItem extends Item {
 public interface Handler {boolean use(PlayerEntity player,String kind,Hand hand);}
 public static final java.util.Map<String,Handler> handlers=new java.util.HashMap<>();private final String kind;
 public CommerceItem(String kind){super(new Settings().maxCount(kind.startsWith("personal_")?1:64).rarity(Rarity.RARE));this.kind=kind;}
 @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){return world.isClient?TypedActionResult.success(player.getStackInHand(hand)):handlers.containsKey(kind)&&handlers.get(kind).use(player,kind,hand)?TypedActionResult.success(player.getStackInHand(hand)):TypedActionResult.fail(player.getStackInHand(hand));}
}
