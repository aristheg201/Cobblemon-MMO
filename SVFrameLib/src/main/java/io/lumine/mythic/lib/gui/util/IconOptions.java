package io.lumine.mythic.lib.gui.util;
import net.minecraft.component.DataComponentTypes; import net.minecraft.component.type.CustomModelDataComponent; import net.minecraft.item.*; import net.minecraft.registry.Registries; import net.minecraft.util.Identifier;
import java.util.*;
public class IconOptions {
    public static final IconOptions EMPTY=new IconOptions();
    private final Item material; private final Integer customModelDataInt; private final Boolean unbreakable;
    public IconOptions(){this(null,null,null);} public IconOptions(Item material){this(material,null,null);} public IconOptions(Item material,Integer model){this(material,model,null);}
    private IconOptions(Item material,Integer model,Boolean unbreakable){this.material=material;this.customModelDataInt=model;this.unbreakable=unbreakable;}
    public Item getMaterialElse(Item fallback){return material==null?fallback:material;} public Item getMaterial(){return material;} public Integer getCustomModelDataInt(){return customModelDataInt;} public String getCustomModelDataString(){return customModelDataInt==null?null:String.valueOf(customModelDataInt);} public String getItemModel(){return null;} public String getSkullTexture(){return null;}
    public ItemStack applyToItemStack(ItemStack stack){if(customModelDataInt!=null)stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelDataComponent(customModelDataInt));return stack;}
    public ItemStack applyToItemMeta(ItemStack stack){return applyToItemStack(stack);}
    public ItemStack toItemStack(){return applyToItemStack(new ItemStack(getMaterialElse(Items.STONE)));}
    public IconOptions combine(IconOptions other){if(other==null)return this;return new IconOptions(other.material!=null?other.material:material,other.customModelDataInt!=null?other.customModelDataInt:customModelDataInt,other.unbreakable!=null?other.unbreakable:unbreakable);}
    public static IconOptions from(Object value){
        if(value instanceof IconOptions i)return i;if(value instanceof ItemStack s)return from(s);if(value instanceof Item i)return new IconOptions(i);
        if(value instanceof Map<?,?> m){Item item=parseItem(m.get("material"));Integer model=integer(m.get("model"));if(model==null)model=integer(m.get("custom-model-data"));return new IconOptions(item,model);}
        if(value!=null)return new IconOptions(parseItem(value));return EMPTY;
    }
    public static IconOptions from(ItemStack stack){if(stack==null||stack.isEmpty())return EMPTY;CustomModelDataComponent c=stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);return new IconOptions(stack.getItem(),c==null?null:c.value());}
    private static Item parseItem(Object value){if(value instanceof Item i)return i;if(value==null)return null;String s=String.valueOf(value).toLowerCase(Locale.ROOT);Identifier id=Identifier.tryParse(s.contains(":")?s:"minecraft:"+s);return id!=null&&Registries.ITEM.containsId(id)?Registries.ITEM.get(id):Items.STONE;}
    private static Integer integer(Object v){try{return v instanceof Number n?n.intValue():v==null?null:Integer.valueOf(String.valueOf(v));}catch(Exception e){return null;}}
    @Override public String toString(){return "IconOptions{"+(material==null?"default":Registries.ITEM.getId(material))+",model="+customModelDataInt+"}";}
}
