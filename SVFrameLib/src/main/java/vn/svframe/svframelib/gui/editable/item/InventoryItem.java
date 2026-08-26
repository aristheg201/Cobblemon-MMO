package vn.svframe.svframelib.gui.editable.item;
import vn.svframe.svframelib.gui.PluginInventory; import vn.svframe.svframelib.gui.editable.GeneratedInventory; import net.minecraft.item.ItemStack; import net.minecraft.server.network.ServerPlayerEntity;
import java.text.DecimalFormat; import java.util.*;
public abstract class InventoryItem<T extends GeneratedInventory> {
    private final List<Integer> slots; private String function; protected final DecimalFormat ONE_DIGIT=new DecimalFormat("0.#");
    public InventoryItem(Map<String,?> config){this(null,config);}
    public InventoryItem(InventoryItem<T> parent,Map<String,?> config){
        this.function=str(config,"function",parent==null?null:parent.function);this.slots=parseSlots(config==null?null:config.get("slots"));
        if(this.slots.isEmpty()&&config!=null&&config.containsKey("slot"))this.slots.add(num(config.get("slot"),0));
        if(this.slots.isEmpty()&&parent!=null)this.slots.addAll(parent.slots);
    }
    public void setFunction(String function){this.function=function;} public String getFunction(){return function;} public List<Integer> getSlots(){return Collections.unmodifiableList(slots);}
    public boolean hasDifferentDisplay(){return false;} public boolean isDisplayed(T inv){return true;} public abstract ItemStack getDisplayedItem(T inv,int index);
    public ServerPlayerEntity getEffectivePlayer(T inv,int index){return inv.getPlayer();}
    public void onClick(T inv,PluginInventory.Click click){}
    private static ArrayList<Integer> parseSlots(Object v){ArrayList<Integer> out=new ArrayList<>();if(v instanceof Collection<?> c)for(Object x:c)out.add(num(x,0));else if(v instanceof Number || v instanceof String)out.add(num(v,0));return out;}
    private static int num(Object v,int f){try{return v instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(v));}catch(Exception e){return f;}}
    private static String str(Map<String,?>m,String k,String f){Object v=m==null?null:m.get(k);return v==null?f:String.valueOf(v);}
}
