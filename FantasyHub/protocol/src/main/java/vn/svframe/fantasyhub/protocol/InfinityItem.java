package vn.svframe.fantasyhub.protocol;
import net.minecraft.item.*;import net.minecraft.entity.player.PlayerEntity;import net.minecraft.util.*;import net.minecraft.world.World;
public final class InfinityItem extends Item {
 public interface Handler {boolean activate(PlayerEntity player,String stone,Hand hand);}
 public static Handler handler;public final String stone;
 public InfinityItem(String stone){super(new Settings().maxCount(1).rarity(Rarity.EPIC));this.stone=stone;}
 @Override public void appendTooltip(ItemStack stack,TooltipContext context,java.util.List<net.minecraft.text.Text> tooltip,net.minecraft.item.tooltip.TooltipType type){tooltip.add(net.minecraft.text.Text.translatable("item.fantasyhub."+(stone.equals("gauntlet")?"infinity_gauntlet":stone+"_stone")+".hint").formatted(net.minecraft.util.Formatting.GRAY));}
 @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){return world.isClient?TypedActionResult.success(player.getStackInHand(hand)):handler!=null&&handler.activate(player,stone,hand)?TypedActionResult.success(player.getStackInHand(hand)):TypedActionResult.fail(player.getStackInHand(hand));}
}
