package io.lumine.mythic.lib.api.util;
import net.minecraft.component.DataComponentTypes; import net.minecraft.component.type.CustomModelDataComponent; import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.*; import net.minecraft.text.Text;
import java.util.*; import java.util.function.*;
public class ItemFactory {
    private ItemStack itemStack; private final List<Text> lore=new ArrayList<>();
    public static ItemFactory of(Item item){return new ItemFactory(new ItemStack(Objects.requireNonNull(item)));}
    public static ItemFactory of(ItemStack stack){return new ItemFactory(Objects.requireNonNull(stack).copy());}
    protected ItemFactory(){this(new ItemStack(Items.STONE));} protected ItemFactory(ItemStack stack){this.itemStack=stack;}
    public ItemFactory transform(Consumer<ItemStack> op){op.accept(itemStack);return this;}
    public ItemFactory name(String name){itemStack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(colorize(name)));return this;}
    public ItemFactory type(Item item){int count=itemStack.getCount();itemStack=new ItemStack(item,count);if(!lore.isEmpty())itemStack.set(DataComponentTypes.LORE,new LoreComponent(List.copyOf(lore)));return this;}
    public ItemFactory lore(String line){if(line!=null)lore.add(Text.literal(colorize(line)));syncLore();return this;}
    public ItemFactory lore(String... lines){if(lines!=null)for(String s:lines)lore(s);return this;}
    public ItemFactory lore(Iterable<String> lines){if(lines!=null)for(String s:lines)lore(s);return this;}
    public ItemFactory lore(Function<List<String>,Iterable<String>> transform){List<String> raw=lore.stream().map(Text::getString).toList();clearLore();return lore(transform.apply(raw));}
    public ItemFactory clearLore(){lore.clear();itemStack.remove(DataComponentTypes.LORE);return this;}
    public ItemFactory amount(int amount){itemStack.setCount(Math.max(0,amount));return this;}
    public ItemFactory model(int model){itemStack.set(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelDataComponent(model));return this;}
    public ItemFactory apply(Consumer<ItemFactory> op){op.accept(this);return this;}
    public ItemStack build(){return itemStack.copy();}
    private void syncLore(){itemStack.set(DataComponentTypes.LORE,new LoreComponent(List.copyOf(lore)));}
    private static String colorize(String s){return s==null?"":s.replace('&','§');}
}
