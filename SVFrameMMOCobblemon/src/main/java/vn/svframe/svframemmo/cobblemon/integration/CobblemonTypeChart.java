package vn.svframe.svframemmo.cobblemon.integration;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

/** Reads the type chart shipped by the installed Cobblemon Showdown engine, once at startup. */
public final class CobblemonTypeChart {
    private static volatile Map<String,Map<String,Integer>> chart=Map.of();
    private CobblemonTypeChart(){}
    public static void load(){
        try{chart=parse(Files.readString(Path.of("showdown","data","typechart.js")));}
        catch(java.io.IOException|IllegalArgumentException failure){chart=Map.of();SVFrameMMOCobblemon.LOG.error("Cobblemon type chart unavailable; elemental player moves cannot execute safely",failure);}
    }
    public static Map<String,Map<String,Integer>> parse(String source){
        var result=new TreeMap<String,Map<String,Integer>>();var types=Pattern.compile("(?m)^  ([a-z0-9]+): \\{\\s*damageTaken: \\{([^}]+)\\}").matcher(source);
        while(types.find()){var damage=new TreeMap<String,Integer>();var entries=Pattern.compile("([a-zA-Z0-9]+):\\s*([0-3])\\b").matcher(types.group(2));while(entries.find())damage.put(entries.group(1).toLowerCase(Locale.ROOT),Integer.parseInt(entries.group(2)));result.put(types.group(1),Map.copyOf(damage));}
        if(result.size()<18)throw new IllegalArgumentException("Type chart has fewer than 18 elemental entries");return Map.copyOf(result);
    }
    public static boolean ready(){return !chart.isEmpty();}
    public static double effectiveness(String attack,Iterable<com.cobblemon.mod.common.api.types.ElementalType> defense){
        double coefficient=1;for(var type:defense){int code=chart.getOrDefault(type.getName().toLowerCase(Locale.ROOT),Map.of()).getOrDefault(attack.toLowerCase(Locale.ROOT),0);coefficient*=switch(code){case 1->2;case 2->.5;case 3->0;default->1;};}return coefficient;
    }
    public static boolean immune(String defense,String status){String key=switch(status){case "burn"->"brn";case "poison","poisonbadly","badpoison"->"psn";case "paralysis"->"par";case "frozen","freeze"->"frz";case "sleep"->"slp";default->status;};return chart.getOrDefault(defense.toLowerCase(Locale.ROOT),Map.of()).getOrDefault(key,0)==3;}
}
