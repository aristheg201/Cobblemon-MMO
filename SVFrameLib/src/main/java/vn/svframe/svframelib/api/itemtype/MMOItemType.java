package vn.svframe.svframelib.api.itemtype;

import vn.svframe.svframelib.api.item.NBTItem;
import net.minecraft.item.ItemStack;
import java.util.Locale;
import java.util.Objects;

public final class MMOItemType implements ItemType {private final String type,id;public MMOItemType(String type,String id){this.type=norm(Objects.requireNonNull(type,"Type cannot be null"));this.id=norm(Objects.requireNonNull(id,"ID cannot be null"));}public boolean matches(ItemStack stack){NBTItem nbt=NBTItem.get(stack);return nbt.getString("MMOITEMS_ITEM_TYPE").equalsIgnoreCase(type)&&nbt.getString("MMOITEMS_ITEM_ID").equalsIgnoreCase(id);}public String display(){return type+"."+id;}@Override public int hashCode(){return Objects.hash(type,id);}@Override public boolean equals(Object o){return o instanceof MMOItemType m&&type.equals(m.type)&&id.equals(m.id);}public String type(){return type;}public String id(){return id;}private static String norm(String s){return s.trim().toUpperCase(Locale.ROOT).replace('-','_').replace(' ','_');}}
