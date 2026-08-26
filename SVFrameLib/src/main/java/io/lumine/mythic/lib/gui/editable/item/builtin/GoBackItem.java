package io.lumine.mythic.lib.gui.editable.item.builtin;
import io.lumine.mythic.lib.gui.PluginInventory; import io.lumine.mythic.lib.gui.editable.GeneratedInventory; import io.lumine.mythic.lib.gui.editable.item.PhysicalItem; import io.lumine.mythic.lib.gui.editable.placeholder.Placeholders; import java.util.*;
public class GoBackItem<T extends GeneratedInventory> extends PhysicalItem<T> {
 public GoBackItem(Map<String,?> c){super(c);} public Placeholders getPlaceholders(T i,int x){return new Placeholders();} public void onClick(T i,PluginInventory.Click c){i.getNavigator().popOpen();}
}