package vn.svframe.svframelib.skill;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

/** Optional shared cast restrictions; class/item/script skills keep their existing owner. */
public final class SkillCastPolicies {
  private record Policy(BiPredicate<UUID,String> allowed,ToDoubleBiFunction<UUID,String> cap) {}
  private static final Map<String,Policy> POLICIES=new ConcurrentHashMap<>();
  private SkillCastPolicies() {}
  public static AutoCloseable register(String owner,BiPredicate<UUID,String> allowed,ToDoubleBiFunction<UUID,String> cap) {
    var value=new Policy(Objects.requireNonNull(allowed),Objects.requireNonNull(cap));
    POLICIES.put(Objects.requireNonNull(owner),value);
    return ()->POLICIES.remove(owner,value);
  }
  public static boolean allows(UUID player,String skill) {
    Objects.requireNonNull(player);Objects.requireNonNull(skill);
    for(var policy:POLICIES.values())if(!policy.allowed.test(player,skill))return false;
    return true;
  }
  /** Apply the policy to the combined skill-specific and global reduction. */
  public static double cooldownSeconds(UUID player,String skill,double base,double modified,double statReduction) {
    if(!Double.isFinite(base)||!Double.isFinite(modified))throw new IllegalArgumentException("Nonfinite cooldown");
    double reduction=cooldownReduction(player,skill,statReduction);
    double cap=cooldownReduction(player,skill,1);
    return Math.max(Math.max(0,base)*(1-cap),Math.max(0,modified)*(1-reduction));
  }
  public static double cooldownReduction(UUID player,String skill,double requested) {
    double result=Double.isFinite(requested)?Math.max(0,Math.min(1,requested)):0;
    for(var policy:POLICIES.values()) {
      double cap=policy.cap.applyAsDouble(player,skill);
      result=Math.min(result,Double.isFinite(cap)?Math.max(0,Math.min(1,cap)):0);
    }
    return result;
  }
}
