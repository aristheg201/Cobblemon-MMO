package vn.svframe.mythiclibfabric.runtime.skill;
import java.util.*;
public final class SkillMetadata {
 private final UUID caster; private final UUID trigger; private final int level; private final Map<String,Double> numeric=new HashMap<>(); private final Map<String,String> text=new HashMap<>();
 public SkillMetadata(UUID caster,UUID trigger,int level){this.caster=Objects.requireNonNull(caster);this.trigger=trigger;this.level=Math.max(1,level);} public UUID caster(){return caster;} public UUID trigger(){return trigger;} public int level(){return level;}
 public SkillMetadata putNumber(String k,double v){if(!Double.isFinite(v))throw new IllegalArgumentException();numeric.put(k,v);return this;} public double getNumber(String k,double d){return numeric.getOrDefault(k,d);} public SkillMetadata putText(String k,String v){text.put(k,v);return this;} public String getText(String k,String d){return text.getOrDefault(k,d);} public Map<String,Double> numbers(){return Map.copyOf(numeric);} public Map<String,String> texts(){return Map.copyOf(text);}
}
