package io.lumine.mythic.lib.comp.adventure;
import io.lumine.mythic.lib.comp.adventure.argument.*;
import io.lumine.mythic.lib.comp.adventure.tag.AdventureTag;
import java.util.*; import java.util.concurrent.*; import java.util.regex.*;
public class AdventureParser {
    private static final Pattern LEGACY=Pattern.compile("(?i)§[0-9A-FK-ORX]|&[0-9A-FK-ORX]");
    private static final Pattern HEX=Pattern.compile("(?i)(?:§x(?:§[0-9A-F]){6}|&#[0-9A-F]{6}|<#?[0-9A-F]{6}>)");
    private static final Pattern TAG=Pattern.compile("<(/?)([a-zA-Z0-9_:-]+)(?::([^>]*))?>");
    private final List<AdventureTag> tags=new CopyOnWriteArrayList<>();
    public AdventureParser(boolean defaults){ } public AdventureParser(){this(true);}
    public String parse(String input){
        if(input==null)return null; String out=input.replace('&','§');
        Matcher m=TAG.matcher(out); StringBuffer sb=new StringBuffer();
        while(m.find()){
            AdventureTag tag=findByName(m.group(2)).orElse(null);
            if(tag==null){m.appendReplacement(sb,Matcher.quoteReplacement(m.group()));continue;}
            if(!m.group(1).isEmpty()){m.appendReplacement(sb,"");continue;}
            List<AdventureArgument> args=new ArrayList<>();String arg=m.group(3);
            if(arg!=null&&!arg.isBlank())for(String s:arg.split(":"))args.add(new AdventureArgument(s));
            String repl=tag.resolver().resolve("",new AdventureArgumentQueue(args));
            m.appendReplacement(sb,Matcher.quoteReplacement(repl==null?"":repl));
        }
        m.appendTail(sb); return sb.toString();
    }
    public CompletableFuture<String> parseAsync(String input){return CompletableFuture.supplyAsync(()->parse(input));}
    public Collection<String> parse(Collection<String> input){return input==null?List.of():input.stream().map(this::parse).toList();}
    public CompletableFuture<Collection<String>> parseAsync(Collection<String> input){return CompletableFuture.supplyAsync(()->parse(input));}
    public String stripColors(String input){if(input==null)return null;return TAG.matcher(HEX.matcher(LEGACY.matcher(input).replaceAll("")).replaceAll("")).replaceAll("");}
    public String lastColor(String input,boolean includeDecorations){
        if(input==null||input.isEmpty())return "";
        String last=""; for(int i=0;i<input.length()-1;i++)if((input.charAt(i)=='§'||input.charAt(i)=='&')){
            char c=Character.toLowerCase(input.charAt(i+1)); boolean decoration="klmno".indexOf(c)>=0;
            if("0123456789abcdefrx".indexOf(c)>=0 || (includeDecorations&&decoration))last="§"+c;
        }
        Matcher hm=HEX.matcher(input); while(hm.find())last=hm.group();
        return last;
    }
    public void add(AdventureTag tag){Objects.requireNonNull(tag);if(findByName(tag.name()).isPresent())throw new IllegalArgumentException("Adventure tag already registered: "+tag.name());tags.add(tag);}
    public void forceRegister(AdventureTag tag){remove(tag);tags.removeIf(t->t.name().equalsIgnoreCase(tag.name()));tags.add(tag);}
    public void remove(AdventureTag tag){tags.remove(tag);}
    public Optional<AdventureTag> findByName(String name){if(name==null)return Optional.empty();return tags.stream().filter(t->t.name().equalsIgnoreCase(name)||t.aliases().stream().anyMatch(a->a.equalsIgnoreCase(name))).findFirst();}
    public List<AdventureTag> tags(){return List.copyOf(tags);}
}
