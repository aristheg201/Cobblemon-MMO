package vn.svframe.svframelib.gui.editable.item.builtin;
import vn.svframe.svframelib.gui.PluginInventory; import vn.svframe.svframelib.gui.editable.GeneratedInventory; import vn.svframe.svframelib.gui.editable.item.PhysicalItem; import vn.svframe.svframelib.gui.editable.placeholder.Placeholders; import java.util.*;
public class CloseInventoryItem<T extends GeneratedInventory> extends PhysicalItem<T> {
 public CloseInventoryItem(Map<String,?> c){super(c);} public Placeholders getPlaceholders(T i,int x){return new Placeholders();} public void onClick(T i,PluginInventory.Click c){i.getNavigator().close();}
}