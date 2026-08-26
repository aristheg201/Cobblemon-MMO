package io.lumine.mythic.lib.gui.editable.item;
import io.lumine.mythic.lib.gui.PluginInventory; import io.lumine.mythic.lib.gui.editable.GeneratedInventory; import io.lumine.mythic.lib.gui.editable.placeholder.Placeholders; import io.lumine.mythic.lib.script.Script;
import java.util.*;
public class SimpleItem<T extends GeneratedInventory> extends PhysicalItem<T> {
    private final Script script;
    public SimpleItem(Map<String,?> config){this(null,config);} public SimpleItem(InventoryItem<T> parent,Map<String,?> config){super(parent,config);Object s=config==null?null:config.get("script");script=s==null?null:io.lumine.mythic.lib.MythicLib.plugin.getSkills().loadScript("gui-"+System.identityHashCode(this),s);}
    @Override public Placeholders getPlaceholders(T inv,int index){Placeholders p=new Placeholders();p.register("page",inv.page+1);p.register("index",index);return p;}
    @Override public void onClick(T inv,PluginInventory.Click click){if(script!=null)script.cast(io.lumine.mythic.lib.skill.SkillMetadata.of(inv.getMMOPlayerData()));}
}
