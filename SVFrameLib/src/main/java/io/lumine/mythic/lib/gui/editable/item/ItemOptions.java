package io.lumine.mythic.lib.gui.editable.item;
import io.lumine.mythic.lib.gui.util.IconOptions; import net.minecraft.item.*;
public record ItemOptions(int index,IconOptions icon) {
    public ItemOptions{if(icon==null)icon=IconOptions.EMPTY;}
    public static ItemOptions index(int i){return new ItemOptions(i,IconOptions.EMPTY);} public static ItemOptions material(int i,Item m){return new ItemOptions(i,new IconOptions(m));}
    public static ItemOptions model(int i,Item m,int model){return new ItemOptions(i,new IconOptions(m,model));} public static ItemOptions item(int i,ItemStack item){return new ItemOptions(i,IconOptions.from(item));}
}
