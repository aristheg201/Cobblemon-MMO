package vn.svframe.svframemobs.mythic;

import java.util.*;

public record MythicDefinition(String id,String source,List<MythicLine> skills,
                               List<MythicLine.Condition> conditions,List<MythicLine.Condition> targetConditions,
                               List<MythicLine.Condition> triggerConditions,double cooldown) {
    public MythicDefinition {
        skills=List.copyOf(skills);conditions=List.copyOf(conditions);targetConditions=List.copyOf(targetConditions);triggerConditions=List.copyOf(triggerConditions);
    }
    public static MythicDefinition parse(String id,String source,Map<String,Object> map) {
        if(id.isBlank()||id.length()>128)throw new IllegalArgumentException("Invalid skill ID");
        for(String key:map.keySet())if(!Set.of("Skills","Conditions","TargetConditions","TriggerConditions","Cooldown").contains(key))throw new IllegalArgumentException(source+": unsupported meta-skill field "+key);
        double cooldown=Double.parseDouble(String.valueOf(map.getOrDefault("Cooldown",0)));
        if(!Double.isFinite(cooldown)||cooldown<0||cooldown>86400)throw new IllegalArgumentException(source+": invalid cooldown");
        return new MythicDefinition(id,source,strings(map.get("Skills")).stream().map(MythicLine::parse).toList(),
                strings(map.get("Conditions")).stream().map(MythicLine::condition).toList(),strings(map.get("TargetConditions")).stream().map(MythicLine::condition).toList(),strings(map.get("TriggerConditions")).stream().map(MythicLine::condition).toList(),cooldown);
    }
    private static List<String> strings(Object value) {
        if(value==null)return List.of();if(!(value instanceof List<?> list))throw new IllegalArgumentException("Expected Mythic YAML list");return list.stream().map(String::valueOf).toList();
    }
}
