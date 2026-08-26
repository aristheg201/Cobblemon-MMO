package vn.svframe.svframelib.util;
import net.minecraft.component.DataComponentTypes; import net.minecraft.component.type.LoreComponent; import net.minecraft.item.ItemStack; import net.minecraft.text.Text;
import java.awt.Color; import java.util.*; import java.util.concurrent.*; import java.util.function.Supplier;
public final class AdventureUtils {
    private AdventureUtils(){}
    public static Optional<net.minecraft.util.Formatting> getByName(String name){if(name==null)return Optional.empty();return Arrays.stream(net.minecraft.util.Formatting.values()).filter(f->f.getName().equalsIgnoreCase(name)||f.name().equalsIgnoreCase(name)).findFirst();}
    public static Optional<net.minecraft.text.TextColor> getByHex(String hex){if(hex==null)return Optional.empty();String s=hex.trim();if(s.startsWith("#"))s=s.substring(1);try{return Optional.of(net.minecraft.text.TextColor.fromRgb(Integer.parseInt(s,16)&0xFFFFFF));}catch(RuntimeException ignored){return Optional.empty();}}
    public static CompletableFuture<Void> runAsync(Runnable r){return CompletableFuture.runAsync(r);}
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> s){return CompletableFuture.supplyAsync(s);}
    public static Color color(String input){if(input==null)return Color.WHITE;String s=input.trim();try{if(s.startsWith("#"))return Color.decode(s);String[] p=s.split(",");if(p.length==3)return new Color(Integer.parseInt(p[0].trim()),Integer.parseInt(p[1].trim()),Integer.parseInt(p[2].trim()));}catch(Exception ignored){}return Color.WHITE;}
    public static ItemStack setDisplayName(ItemStack item,String name){item.set(DataComponentTypes.CUSTOM_NAME,Text.literal(name==null?"":name.replace('&','§')));return item;}
    public static ItemStack setLore(ItemStack item,List<String> lore){List<Text> parsed=new ArrayList<>();if(lore!=null)for(String line:lore)parsed.add(Text.literal(line.replace('&','§')));item.set(DataComponentTypes.LORE,new LoreComponent(parsed));return item;}
}
