package vn.svframe.svframelib.gui.editable;
import vn.svframe.svframelib.gui.*; import vn.svframe.svframelib.gui.editable.item.*; import net.minecraft.inventory.*; import net.minecraft.item.ItemStack;
import java.util.*; import java.util.concurrent.*; import java.util.function.*;
public abstract class GeneratedInventory extends PluginInventory {
    private final EditableInventory editable; protected final String guiName; private final List<InventoryItem<?>> loaded=new ArrayList<>(); protected Inventory lastOpened; private int perPage;
    public GeneratedInventory(Navigator navigator,EditableInventory editable){super(navigator);this.editable=Objects.requireNonNull(editable);this.guiName=editable.getName();}
    public List<InventoryItem<?>> getLoaded(){return List.copyOf(loaded);} public EditableInventory getEditable(){return editable;} protected void enablePagination(int perPage){this.perPage=Math.max(1,perPage);} public boolean hasPagination(){return perPage>0;}
    public int getMaxPage(){return computeMaxPage(loaded.size());} public int getPageIndex(int index){return perPage<=0?index:index+page*perPage;} public int computeMaxPage(int amount){return perPage<=0?0:Math.max(0,(amount-1)/perPage);}
    public InventoryItem<?> getByFunction(String f){InventoryItem<?> i=loaded.stream().filter(x->f.equalsIgnoreCase(x.getFunction())).findFirst().orElse(null);return i==null?editable.getByFunction(f):i;}
    public InventoryItem<?> getBySlot(int slot){for(InventoryItem<?> i:loaded)if(i.getSlots().contains(slot))return i;for(InventoryItem<?> i:editable.getItems())if(i.getSlots().contains(slot))return i;return null;}
    public void addLoaded(InventoryItem<?> item){if(item!=null)loaded.add(item);}
    @Override public Inventory getInventory(){int size=Math.max(9,Math.min(54,((editable.getVanillaSlots()+8)/9)*9));SimpleInventory inv=new SimpleInventory(size);for(InventoryItem<?> i:editable.getItems())displayItem(inv,i);for(InventoryItem<?> i:loaded)displayItem(inv,i);lastOpened=inv;return inv;}
    @SuppressWarnings({"rawtypes","unchecked"}) public void displayItem(Inventory inv,InventoryItem item){if(!item.isDisplayed(this))return;int idx=0;for(int slot:(List<Integer>)item.getSlots())if(slot>=0&&slot<inv.size())inv.setStack(slot,item.getDisplayedItem(this,idx++));}
    @Override @SuppressWarnings({"rawtypes","unchecked"}) public void onClick(Click click){InventoryItem item=getBySlot(click.slot());if(item!=null)item.onClick(this,click);}
    @Override public void onClose(){} @Override public String getTitle(){return bakeName();} public String getRawName(){return guiName;} public String bakeName(){return applyNamePlaceholders(getRawName()).replace('&','§');} public String applyNamePlaceholders(String s){return s==null?"":s.replace("{page}",String.valueOf(page+1));}
    public void liveUpdate(InventoryItem<?> item,int index,ItemStack stack,Consumer<ItemStack> op){op.accept(stack);if(lastOpened!=null&&index>=0&&index<lastOpened.size())lastOpened.setStack(index,stack);}
    @SuppressWarnings({"rawtypes","unchecked"}) private ItemStack displayOf(InventoryItem<?> item,int index){return ((InventoryItem)item).getDisplayedItem(this,index);}
    public void liveUpdate(InventoryItem<?> item,int index){if(lastOpened!=null&&index>=0&&index<lastOpened.size())lastOpened.setStack(index,displayOf(item,index));}
    public void asyncUpdate(InventoryItem<?> item,int index,ItemStack stack,Consumer<ItemStack> op){CompletableFuture.runAsync(()->op.accept(stack)).thenRun(()->liveUpdate(item,index,stack,x->{}));}
    public void asyncUpdate(InventoryItem<?> item,int index){CompletableFuture.supplyAsync(()->displayOf(item,index)).thenAccept(stack->{if(lastOpened!=null&&index>=0&&index<lastOpened.size())lastOpened.setStack(index,stack);});}
    public <T> void asyncUpdate(CompletableFuture<T> future,InventoryItem<?> item,int index,ItemStack stack,BiConsumer<T,ItemStack> op){future.thenAccept(v->{op.accept(v,stack);liveUpdate(item,index,stack,x->{});});}
}
