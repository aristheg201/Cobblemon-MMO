package vn.svframe.svframemobs.mythic;

import vn.svframe.svframelib.api.MMOLineConfig;
import java.util.*;

/** Parsed original Mythic syntax, with independent mechanic and selector maps. */
public record MythicLine(String mechanic, Map<String,String> parameters, Selector selector,
                         double chance, String trigger, int interval) {
    public record Selector(String name, Map<String,String> parameters, List<Condition> conditions) {
        public Selector { parameters=Map.copyOf(parameters);conditions=List.copyOf(conditions); }
    }
    public record Condition(String name,Map<String,String> parameters,boolean expected,String castInstead) {
        public Condition {parameters=Map.copyOf(parameters);}
    }
    public MythicLine {parameters=Map.copyOf(parameters);}

    public static MythicLine parse(String source) {
        List<String> words=words(source);
        if(words.isEmpty())throw new IllegalArgumentException("Empty skill line");
        MMOLineConfig action=new MMOLineConfig(words.getFirst());
        Map<String,String> parameters=parameters(words.getFirst());
        Selector selector=null;double chance=1;String trigger="";int interval=20;
        for(int i=1;i<words.size();i++) {
            String word=words.get(i);
            if(word.startsWith("@")) {
                if(selector!=null)throw new IllegalArgumentException("Multiple targeters in "+source);
                MMOLineConfig target=new MMOLineConfig(word.substring(1));Map<String,String> map=parameters(word.substring(1));
                String conditions=map.remove("conditions");
                selector=new Selector(target.getKey().toLowerCase(Locale.ROOT),map,conditions==null?List.of():inlineConditions(conditions));
            } else if(word.startsWith("~")) {
                if(!trigger.isEmpty())throw new IllegalArgumentException("Multiple triggers in "+source);
                String[] parts=word.substring(1).split(":",2);trigger=parts[0].toLowerCase(Locale.ROOT);
                if(parts.length==2) {
                    if(!trigger.equals("ontimer"))throw new IllegalArgumentException("Unsupported trigger argument: "+word);
                    interval=Integer.parseInt(parts[1]);
                }
            } else if(action.getKey().equalsIgnoreCase("delay")&&i==1) parameters.put("ticks",word);
            else chance=Double.parseDouble(word);
        }
        if(!Double.isFinite(chance)||chance<0||chance>1||interval<1||interval>72000)throw new IllegalArgumentException("Invalid chance/timer in "+source);
        return new MythicLine(action.getKey().toLowerCase(Locale.ROOT),parameters,selector,chance,trigger,interval);
    }
    public static Condition condition(String source) {
        List<String> words=words(source);if(words.isEmpty())throw new IllegalArgumentException("Empty condition");
        MMOLineConfig config=new MMOLineConfig(words.getFirst());boolean expected=true;String instead="";
        if(words.size()>1) {
            switch(words.get(1).toLowerCase(Locale.ROOT)) {
                case "true","required" -> {if(words.size()!=2)throw new IllegalArgumentException("Unexpected condition suffix: "+source);}
                case "false" -> {expected=false;if(words.size()!=2)throw new IllegalArgumentException("Unexpected condition suffix: "+source);}
                case "castinstead" -> {if(words.size()!=3)throw new IllegalArgumentException("castinstead requires a skill: "+source);instead=words.get(2);}
                default -> throw new IllegalArgumentException("Unsupported condition action: "+source);
            }
        }
        return new Condition(config.getKey().toLowerCase(Locale.ROOT),parameters(words.getFirst()),expected,instead);
    }
    public static List<Condition> inlineConditions(String source) {
        String value=source.trim();if(!value.startsWith("[")||!value.endsWith("]"))throw new IllegalArgumentException("Inline conditions require [...] ");
        value=value.substring(1,value.length()-1).trim();if(value.isEmpty())return List.of();
        if(!value.startsWith("- "))throw new IllegalArgumentException("Inline conditions require list items");
        List<Condition> result=new ArrayList<>();StringBuilder item=new StringBuilder();int braces=0;char quote=0;
        value=value.substring(2);
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(quote!=0){if(c==quote&&(i==0||value.charAt(i-1)!='\\'))quote=0;}
            else if(c=='\''||c=='"')quote=c;
            else if(c=='{')braces++;
            else if(c=='}')braces--;
            if(quote==0&&braces==0&&Character.isWhitespace(c)&&i+2<value.length()&&value.charAt(i+1)=='-'&&Character.isWhitespace(value.charAt(i+2))) {
                result.add(condition(item.toString()));item.setLength(0);i+=2;
            }else item.append(c);
        }
        result.add(condition(item.toString()));return List.copyOf(result);
    }
    static Map<String,String> parameters(String source) {
        Map<String,String> result=new LinkedHashMap<>();int open=source.indexOf('{');if(open<0)return result;
        String body=source.substring(open+1,source.lastIndexOf('}'));List<String> fields=new ArrayList<>();int start=0,depth=0;char quote=0;
        for(int i=0;i<body.length();i++) {
            char ch=body.charAt(i);
            if(quote!=0){if(ch==quote&&(i==0||body.charAt(i-1)!='\\'))quote=0;}
            else if(ch=='\''||ch=='"')quote=ch;
            else if(ch=='{'||ch=='['||ch=='(')depth++;
            else if(ch=='}'||ch==']'||ch==')')depth--;
            else if(ch==';'&&depth==0){fields.add(body.substring(start,i));start=i+1;}
        }
        fields.add(body.substring(start));
        for(String field:fields) {
            if(field.isBlank())continue;int separator=field.indexOf('=');
            if(separator<1)throw new IllegalArgumentException("Mechanic parameters require key=value: "+field);
            String key=field.substring(0,separator).trim().toLowerCase(Locale.ROOT),value=field.substring(separator+1).trim();
            if(value.length()>=2&&((value.startsWith("\"")&&value.endsWith("\""))||(value.startsWith("'")&&value.endsWith("'"))))value=value.substring(1,value.length()-1);
            if(result.putIfAbsent(key,value)!=null)throw new IllegalArgumentException("Duplicate mechanic parameter "+key);
        }
        return result;
    }
    public static List<String> words(String source) {
        List<String> result=new ArrayList<>();StringBuilder current=new StringBuilder();int braces=0,brackets=0;char quote=0;
        for(int i=0;i<source.length();i++) {
            char c=source.charAt(i);
            if(quote!=0){if(c==quote&&(i==0||source.charAt(i-1)!='\\'))quote=0;}
            else if(c=='\''||c=='"')quote=c;
            else if(c=='{')braces++;else if(c=='}')braces--;
            else if(c=='[')brackets++;else if(c==']')brackets--;
            if(braces<0||brackets<0)throw new IllegalArgumentException("Unbalanced skill syntax: "+source);
            if(Character.isWhitespace(c)&&braces==0&&brackets==0&&quote==0){if(!current.isEmpty()){result.add(current.toString());current.setLength(0);}}else current.append(c);
        }
        if(braces!=0||brackets!=0||quote!=0)throw new IllegalArgumentException("Unbalanced skill syntax: "+source);
        if(!current.isEmpty())result.add(current.toString());return result;
    }
    public String value(String key,String fallback){return parameters.getOrDefault(key,fallback);}
}
