package vn.svframe.svframelib.api.placeholders;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MythicPlaceholders {
    private static final List<MythicPlaceholder> PLACEHOLDERS=new CopyOnWriteArrayList<>();
    private MythicPlaceholders(){}
    public static String parse(String input,Object... contexts){String result=input==null?"":input;if(contexts==null)return result;for(Object context:contexts)for(MythicPlaceholder placeholder:getParserFor(context))result=parseWithPlaceholder(result,context,placeholder);return result;}
    public static String parseWithPlaceholder(String input,Object context,MythicPlaceholder placeholder){if(input==null)return "";String token="<"+placeholder.getMythicIdentifier()+".";int at=0;StringBuilder out=new StringBuilder();while(true){int start=input.indexOf(token,at);if(start<0){out.append(input,at,input.length());break;}out.append(input,at,start);int end=input.indexOf('>',start+token.length());if(end<0){out.append(input.substring(start));break;}String arg=input.substring(start+token.length(),end);String replacement=placeholder.parse(arg,context);out.append(replacement==null?"":replacement);at=end+1;}return out.toString();}
    public static ArrayList<MythicPlaceholder> getParserFor(Object context){ArrayList<MythicPlaceholder> out=new ArrayList<>();for(MythicPlaceholder p:PLACEHOLDERS)if(p.forUseWith(context))out.add(p);return out;}
    public static void registerPlaceholder(MythicPlaceholder placeholder){Objects.requireNonNull(placeholder);PLACEHOLDERS.removeIf(old->old.getMythicIdentifier().equalsIgnoreCase(placeholder.getMythicIdentifier())&&old.getAuthorName().equalsIgnoreCase(placeholder.getAuthorName()));PLACEHOLDERS.add(placeholder);}
}
