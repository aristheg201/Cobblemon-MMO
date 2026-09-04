package vn.svframe.svframelib.gui.editable.item;
import vn.svframe.svframelib.api.util.ItemFactory; import vn.svframe.svframelib.gui.editable.GeneratedInventory; import vn.svframe.svframelib.gui.editable.placeholder.Placeholders; import vn.svframe.svframelib.gui.util.IconOptions; import net.minecraft.item.*;
import java.util.*;
public abstract class PhysicalItem<T extends GeneratedInventory> extends InventoryItem<T> {
    private final String id; private final IconOptions iconOptions; private final String name; private final List<String> lore;
    public PhysicalItem(Map<String,?> config){this(null,config);} public PhysicalItem(InventoryItem<T> parent,Map<String,?> config){super(parent,config);id=str(config,"id",getFunction());iconOptions=IconOptions.from(config==null?null:(config.containsKey("item")?config.get("item"):config.get("material")));name=str(config,"name","");Object raw=config==null?null:config.get("lore");lore=raw instanceof Collection<?> c?c.stream().map(String::valueOf).toList():raw==null?List.of():List.of(String.valueOf(raw));}
    public String getId(){return id;} public void preprocessMeta(T inv,int index,ItemStack item){} public void preprocessLore(T inv,int index,List<String> lore){} public String preprocessName(T inv,int index,String name){return name;}
    @Override public ItemStack getDisplayedItem(T inv,int index){return getDisplayedItem(inv,new ItemOptions(index,iconOptions));}
    public ItemStack getDisplayedItem(T inv,ItemOptions options){
        ItemStack item=options.icon().combine(iconOptions).toItemStack();Placeholders p=getPlaceholders(inv,options.index());List<String> lines=new ArrayList<>(lore);preprocessLore(inv,options.index(),lines);List<String>baked=new ArrayList<>(lines.size());for(String s:lines)baked.add(p.apply(inv.getPlayer(),s));
        ItemFactory f=ItemFactory.of(item);String n=p.apply(inv.getPlayer(),preprocessName(inv,options.index(),name));if(n!=null&&!n.isEmpty())f.name(n);f.lore(baked);item=f.build();preprocessMeta(inv,options.index(),item);return item;
    }
    public abstract Placeholders getPlaceholders(T inv,int index);
    private static String str(Map<String,?>m,String k,String f){Object v=m==null?null:m.get(k);return v==null?f:String.valueOf(v);}
}
