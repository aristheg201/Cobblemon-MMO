package io.lumine.mythic.lib.gui.editable.item.builtin;
import io.lumine.mythic.lib.gui.PluginInventory; import io.lumine.mythic.lib.gui.editable.GeneratedInventory; import io.lumine.mythic.lib.gui.editable.item.PhysicalItem; import io.lumine.mythic.lib.gui.editable.placeholder.Placeholders; import java.util.*;
public class PreviousPageItem<T extends GeneratedInventory> extends PhysicalItem<T> {
 private final boolean hideIfNoPage; public PreviousPageItem(Map<String,?> c){super(c);hideIfNoPage=c!=null&&Boolean.parseBoolean(String.valueOf(c.containsKey("hide-if-no-page")?c.get("hide-if-no-page"):false));}
 public Placeholders getPlaceholders(T i,int x){Placeholders p=new Placeholders();p.register("page",Math.max(1,i.page));return p;} public boolean isDisplayed(T i){return !hideIfNoPage||i.page>0;}
 public void onClick(T i,PluginInventory.Click c){if(i.page>0){i.page--;i.open();}}
}