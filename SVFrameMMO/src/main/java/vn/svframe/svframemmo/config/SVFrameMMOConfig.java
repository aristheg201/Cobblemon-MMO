package vn.svframe.svframemmo.config;

import vn.svframe.svframelib.config.YamlLite;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public record SVFrameMMOConfig(int resourceTickPeriod, int combatTimerSeconds, int autosaveSeconds,
                               int defaultLevel, int defaultClassPoints, int defaultSkillPoints,
                               int defaultAttributePoints, double defaultMana, double defaultStamina, double defaultStellium) {
    public static SVFrameMMOConfig load(Path file) throws IOException {
        Map<String,Object> root = YamlLite.map(YamlLite.parse(file));
        Map<String,Object> defaults = map(root.get("default-playerdata"));
        Map<String,Object> autosave = map(root.get("auto-save"));
        Map<String,Object> combat = map(root.get("combat-log"));
        return new SVFrameMMOConfig(
                integer(root.get("player_resource_tick_period"), 20), integer(combat.get("timer"), 10), integer(autosave.get("interval"), 1800),
                integer(defaults.get("level"), 1), integer(defaults.get("class-points"), 0), integer(defaults.get("skill-points"), 0),
                integer(defaults.get("attribute-points"), 0), number(defaults.get("mana"), 1000), number(defaults.get("stamina"), 1000), number(defaults.get("stellium"), 1000));
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object v){ return v instanceof Map<?,?> m ? (Map<String,Object>)m : Map.of(); }
    private static int integer(Object v,int f){ try{return v instanceof Number n?n.intValue():v==null?f:Integer.parseInt(v.toString());}catch(Exception e){return f;} }
    private static double number(Object v,double f){ try{return v instanceof Number n?n.doubleValue():v==null?f:Double.parseDouble(v.toString());}catch(Exception e){return f;} }
}
