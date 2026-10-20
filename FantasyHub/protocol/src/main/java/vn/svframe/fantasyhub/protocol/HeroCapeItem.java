package vn.svframe.fantasyhub.protocol;

import java.util.List;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Ordinary back-slot equipment; power conditions remain server-owned. */
final class HeroCapeItem extends Item {
  private final String description;
  HeroCapeItem(Settings settings,String description){super(settings);this.description=description;}
  @Override public void appendTooltip(ItemStack stack,TooltipContext context,List<Text> tooltip,TooltipType type){tooltip.add(Text.translatable(description).formatted(Formatting.GRAY));tooltip.add(Text.translatable("fantasyhub.flight.control").formatted(Formatting.GRAY));}
}
