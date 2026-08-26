package io.lumine.mythic.lib.gui.editable.item.builtin;
import io.lumine.mythic.lib.gui.PluginInventory; import io.lumine.mythic.lib.gui.editable.GeneratedInventory; import io.lumine.mythic.lib.gui.editable.item.PhysicalItem; import io.lumine.mythic.lib.gui.editable.placeholder.Placeholders; import java.util.*;
public class NextPageItem<T extends GeneratedInventory> extends PhysicalItem<T> {
 private final boolean hideIfNoPage; public NextPageItem(Map<String,?> c){super(c);hideIfNoPage=c!=null&&Boolean.parseBoolean(String.valueOf(c.containsKey("hide-if-no-page")?c.get("hide-if-no-page"):false));}
 public Placeholders getPlaceholders(T i,int x){Placeholders p=new Placeholders();p.register("page",i.page+2);return p;} public boolean isDisplayed(T i){return !hideIfNoPage||i.page<i.getMaxPage();}
 public void onClick(T i,PluginInventory.Click c){if(i.page<i.getMaxPage()){i.page++;i.open();}}
}