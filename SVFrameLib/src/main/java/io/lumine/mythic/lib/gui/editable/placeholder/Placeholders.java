package io.lumine.mythic.lib.gui.editable.placeholder;
import net.minecraft.server.network.ServerPlayerEntity; import java.util.*; import java.util.regex.*;
public class Placeholders {
    private static final Pattern TOKEN=Pattern.compile("%([^%]+)%|\\{([^{}]+)}"); private final Map<String,String> placeholders=new LinkedHashMap<>(); private String fallback="";
    public void register(String key,Object value){placeholders.put(norm(key),value==null?"":String.valueOf(value));} public void setFallback(String value){fallback=value==null?"":value;}
    public String apply(ServerPlayerEntity player,String input){if(input==null)return null;Matcher m=TOKEN.matcher(input);StringBuffer b=new StringBuffer();while(m.find()){String key=norm(m.group(1)!=null?m.group(1):m.group(2));String v=placeholders.get(key);if(v==null)v=parsePlaceholder(key);if(v==null)v=fallback;m.appendReplacement(b,Matcher.quoteReplacement(v));}m.appendTail(b);return b.toString();}
    public String parsePlaceholder(String key){return placeholders.get(norm(key));} private static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replace('-','_');}
}
