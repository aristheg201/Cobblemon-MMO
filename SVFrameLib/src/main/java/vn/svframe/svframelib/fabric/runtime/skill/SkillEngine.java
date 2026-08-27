package vn.svframe.svframelib.fabric.runtime.skill;
import java.util.*; import java.util.concurrent.ConcurrentHashMap;
public final class SkillEngine {
 public interface Skill { String id(); long cooldownMillis(SkillMetadata m); boolean canCast(SkillMetadata m); CastResult cast(SkillMetadata m); }
 public record CastResult(boolean success,String reason){public static CastResult ok(){return new CastResult(true,"");} public static CastResult fail(String r){return new CastResult(false,r);}}
 private final Map<String,Skill> skills=new ConcurrentHashMap<>(); private final Map<UUID,CooldownMap> cooldowns=new ConcurrentHashMap<>();
 public void register(Skill skill){Skill old=skills.putIfAbsent(norm(skill.id()),Objects.requireNonNull(skill));if(old!=null)throw new IllegalStateException("duplicate skill "+skill.id());}
 public Optional<Skill> find(String id){return Optional.ofNullable(skills.get(norm(id)));} public Collection<Skill> skills(){return List.copyOf(skills.values());}
 public CastResult cast(String id,SkillMetadata meta,long now){Skill s=skills.get(norm(id));if(s==null)return CastResult.fail("unknown-skill"); CooldownMap map=cooldowns.computeIfAbsent(meta.caster(),x->new CooldownMap()); if(!map.ready(id,now))return CastResult.fail("cooldown:"+map.remaining(id,now)); if(!s.canCast(meta))return CastResult.fail("requirements"); CastResult r=Objects.requireNonNull(s.cast(meta)); if(r.success()){long cd=Math.max(0,s.cooldownMillis(meta));map.trigger(id,now,cd);} return r;}
 private static String norm(String s){return Objects.requireNonNull(s).trim().toLowerCase(Locale.ROOT);}
}
