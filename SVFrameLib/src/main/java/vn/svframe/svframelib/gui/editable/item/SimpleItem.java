package vn.svframe.svframelib.gui.editable.item;
import vn.svframe.svframelib.gui.PluginInventory; import vn.svframe.svframelib.gui.editable.GeneratedInventory; import vn.svframe.svframelib.gui.editable.placeholder.Placeholders; import vn.svframe.svframelib.script.Script;
import java.util.*;
public class SimpleItem<T extends GeneratedInventory> extends PhysicalItem<T> {
    private final Script script;
    public SimpleItem(Map<String,?> config){this(null,config);} public SimpleItem(InventoryItem<T> parent,Map<String,?> config){super(parent,config);Object s=config==null?null:config.get("script");script=s==null?null:vn.svframe.svframelib.SVFrameLib.plugin.getSkills().loadScript("gui-"+System.identityHashCode(this),s);}
    @Override public Placeholders getPlaceholders(T inv,int index){Placeholders p=new Placeholders();p.register("page",inv.page+1);p.register("index",index);return p;}
    @Override public void onClick(T inv,PluginInventory.Click click){if(script!=null)script.cast(vn.svframe.svframelib.skill.SkillMetadata.of(inv.getMMOPlayerData()));}
}
