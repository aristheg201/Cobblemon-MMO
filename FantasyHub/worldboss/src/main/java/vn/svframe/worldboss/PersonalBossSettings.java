package vn.svframe.worldboss;
import com.google.gson.Gson;
import java.util.*;
/** Immutable per-encounter stat copies; scheduled definitions are never edited by ticket use. */
public final class PersonalBossSettings {
 public double hp=.55,damage=.70,mitigation=.75,heavyCooldown=1.30,telegraph=1.35;
 public int reconnectGraceSeconds=90;
 public List<String> standardBosses=new ArrayList<>(List.of("thor","web_guardian","speedster")),eliteBosses=new ArrayList<>(List.of("thanos","collector","sorcerer_guardian","ultimate_trial"));
 public void validate(){for(double v:new double[]{hp,damage,mitigation,heavyCooldown,telegraph})if(!Double.isFinite(v)||v<=0||v>5)throw new IllegalArgumentException("Invalid personal encounter multiplier");if(reconnectGraceSeconds<10||reconnectGraceSeconds>600)throw new IllegalArgumentException("Invalid reconnect grace");}
 public BossConfig.Boss snapshot(BossConfig.Boss original){validate();var json=new Gson();var copy=json.fromJson(json.toJson(original),BossConfig.Boss.class);copy.health=Math.max(1,copy.health*hp);copy.damageMultiplier*=damage;copy.damageMitigation=Math.min(.99,copy.damageMitigation*mitigation);copy.cooldownMultiplier*=heavyCooldown;copy.windupSeconds=Math.max(copy.windupSeconds+1,(int)Math.ceil(copy.windupSeconds*telegraph));copy.mechanics.put("telegraphMultiplier",telegraph);return copy;}
}
