package vn.svframe.svframelib.gui.editable;
import vn.svframe.svframelib.gui.editable.item.*; import vn.svframe.svframelib.gui.editable.item.builtin.*; import vn.svframe.svframelib.module.MMOPlugin;
import java.util.*;
public abstract class EditableInventory {
    private final String id; private String name; private int vanillaSlots=54; private final List<InventoryItem<?>> items=new ArrayList<>();
    public EditableInventory(String id){this.id=Objects.requireNonNull(id);this.name=id;}
    public void reload(MMOPlugin plugin,Map<String,?> section){items.clear();name=str(section,"name",id);vanillaSlots=Math.max(9,Math.min(54,num(section==null?null:section.get("slots"),54)));Object raw=section==null?null:section.get("items");if(raw instanceof Map<?,?> map)for(var e:map.entrySet())if(e.getValue() instanceof Map<?,?> cfg){Map<String,Object> c=copy(cfg);c.putIfAbsent("function",String.valueOf(e.getKey()));InventoryItem<?> item=resolveEntry(c);if(item!=null)items.add(item);}}
    public String getId(){return id;} public List<InventoryItem<?>> getItems(){return List.copyOf(items);} public String getName(){return name;} public int getVanillaSlots(){return vanillaSlots;}
    public InventoryItem<?> getByFunction(String function){if(function==null)return null;return items.stream().filter(i->function.equalsIgnoreCase(i.getFunction())).findFirst().orElse(null);}
    private InventoryItem<?> resolveEntry(Map<String,Object> c){String f=str(c,"function","");return switch(f.toLowerCase(Locale.ROOT)){case "close"->new CloseInventoryItem<>(c);case "back","go-back"->new GoBackItem<>(c);case "next","next-page"->new NextPageItem<>(c);case "previous","previous-page"->new PreviousPageItem<>(c);default->resolveItem(f,c);};}
    public abstract InventoryItem<?> resolveItem(String function,Map<String,Object> config);
    private static int num(Object v,int f){try{return v instanceof Number n?n.intValue():v==null?f:Integer.parseInt(String.valueOf(v));}catch(Exception e){return f;}}
    private static String str(Map<String,?>m,String k,String f){Object v=m==null?null:m.get(k);return v==null?f:String.valueOf(v);}
    private static Map<String,Object> copy(Map<?,?>m){Map<String,Object>r=new LinkedHashMap<>();m.forEach((k,v)->r.put(String.valueOf(k),v));return r;}
}
