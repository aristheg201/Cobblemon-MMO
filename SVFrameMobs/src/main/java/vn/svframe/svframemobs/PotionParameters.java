package vn.svframe.svframemobs;

import java.util.*;

/** Shared Mythic potion aliases. In particular p is the particle flag, not the potion type. */
public final class PotionParameters {
    private PotionParameters() {}
    public static Map<String,String> normalize(Map<String,String> input, boolean expressions, Set<String> extra) {
        Map<String,String> values = new LinkedHashMap<>(input);
        alias(values,"type","t","potion");
        alias(values,"duration","d"); alias(values,"level","l","lvl");
        alias(values,"hasparticles","particles","p");
        for(String key: values.keySet())
            if(!Set.of("type","duration","level","hasparticles","ambient","icon","overwrite").contains(key)&&!extra.contains(key))
                throw new IllegalArgumentException("Unsupported potion parameter "+key);
        for(String flag: List.of("hasparticles","ambient","icon","overwrite"))
            if(values.containsKey(flag)&&!Set.of("true","false").contains(values.get(flag).toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Invalid potion flag "+flag);
        validateNumber(values,"duration",1,Integer.MAX_VALUE,expressions);
        validateNumber(values,"level",0,255,expressions);
        return values;
    }
    private static void alias(Map<String,String> values,String key,String... aliases) {
        for(String alias: aliases) {
            String value=values.remove(alias);
            if(value==null)continue;
            String previous=values.putIfAbsent(key,value);
            if(previous!=null&&!previous.equalsIgnoreCase(value))
                throw new IllegalArgumentException("Conflicting potion aliases for "+key);
        }
    }
    private static void validateNumber(Map<String,String> values,String key,int min,int max,boolean expressions) {
        if(!values.containsKey(key))return;
        double value;
        try { value=Double.parseDouble(values.get(key)); }
        catch(NumberFormatException invalid) {
            if(expressions)return;
            throw new IllegalArgumentException("Invalid potion "+key,invalid);
        }
        integer(value,min,max);
    }
    public static int integer(double value,int min,int max) {
        if(!Double.isFinite(value)||value<min||value>max||value!=Math.rint(value))
            throw new IllegalArgumentException("Invalid potion integer "+value);
        return (int)value;
    }
    public static String type(String raw) {
        return switch(raw.toUpperCase(Locale.ROOT)) {
            case "SLOW"->"slowness";case "FAST_DIGGING"->"haste";case "SLOW_DIGGING"->"mining_fatigue";
            case "INCREASE_DAMAGE"->"strength";case "DAMAGE_RESISTANCE"->"resistance";
            case "CONFUSION"->"nausea";case "JUMP"->"jump_boost";
            case "HEAL"->"instant_health";case "HARM"->"instant_damage";default->raw;
        };
    }
}
