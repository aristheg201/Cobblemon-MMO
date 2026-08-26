package vn.svframe.svframelib.api.itemtype;

import vn.svframe.svframelib.api.item.NBTItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public interface ItemType {
    boolean matches(ItemStack stack);String display();
    static ItemType fromString(String input){if(input==null||input.isBlank())throw new IllegalArgumentException("Item type cannot be blank");if(input.matches(".*[.%?].*")){String[] split=input.split("[.%?]",-1);if(split.length!=2||split[0].isBlank()||split[1].isBlank())throw new IllegalArgumentException("Please specify a type and ID");return new MMOItemType(split[0],split[1]);}Identifier id=Identifier.tryParse(input.toLowerCase(java.util.Locale.ROOT));if(id==null)throw new IllegalArgumentException("Invalid item id: "+input);return new VanillaType(Registries.ITEM.get(id));}
    static ItemType fromItemStack(ItemStack stack){NBTItem nbt=NBTItem.get(stack);if(nbt.hasTag("MMOITEMS_ITEM_TYPE"))return new MMOItemType(nbt.getString("MMOITEMS_ITEM_TYPE"),nbt.getString("MMOITEMS_ITEM_ID"));return new VanillaType(stack.getItem());}
}
