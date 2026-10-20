package vn.svframe.fantasyhub.client;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;

/** Optional presentation arbitration; all attacks still belong to Better Combat and SVFrame. */
public final class BetterCombatCompat {
  private static final Map<Integer,Long> ATTACKS=new HashMap<>();
  private static long tick,starts,skillStops,pausedFrames;
  private static Method stop,mainPose,offPose;
  private static Constructor<?> pose;
  private static boolean loaded,emfHook;
  private static String diagnostic="not installed";
  private BetterCombatCompat() {}
  static void install() {
    loaded=FabricLoader.getInstance().isModLoaded("bettercombat");
    try {
      if(loaded) {
        stop=Class.forName("net.bettercombat.client.animation.PlayerAttackAnimatable").getMethod("stopAttackAnimation",float.class);
        var type=Class.forName("net.bettercombat.api.EntityPlayer_BetterCombat");
        mainPose=type.getMethod("getMainHandIdleAnimation");offPose=type.getMethod("getOffHandIdleAnimation");
        // Resolve the actual API return type: released 2.4.0 uses logic.Pose,
        // while other revisions nest this record inside PlayerAttackHelper.
        pose=Class.forName("net.bettercombat.logic.PlayerAttackHelper")
          .getMethod("poseForPlayer",net.minecraft.entity.player.PlayerEntity.class)
          .getReturnType().getConstructor(String.class,String.class);
        diagnostic="Better Combat "+FabricLoader.getInstance().getModContainer("bettercombat").orElseThrow().getMetadata().getVersion().getFriendlyString();
      } else diagnostic="Better Combat absent; native combat retained";
    }catch(ReflectiveOperationException|RuntimeException|LinkageError error){fail(error);}
    // Switch to the intact vanilla rig during external poses, instead of freezing
    // Detailed Animations' segmented child transforms mid-stride. This also works without Better Combat,
    // or when an unsupported optional Better Combat API is encountered.
    try {
      if(FabricLoader.getInstance().isModLoaded("entity_model_features")) {
        var api=Class.forName("traben.entity_model_features.EMFAnimationApi");
        emfHook=Boolean.TRUE.equals(api.getMethod("registerVanillaModelCondition",Function.class).invoke(null,(Function<Object,Boolean>)BetterCombatCompat::ownsAnimation));
      }
    }catch(ReflectiveOperationException|RuntimeException|LinkageError error){fail(error);}
  }
  private static void fail(Throwable error) {
    diagnostic=error.getClass().getSimpleName()+": "+error.getMessage();
    org.slf4j.LoggerFactory.getLogger("FantasyHub/Combat-Compat").warn("Optional animation compatibility: {}",diagnostic);
  }
  static void tick(long now){tick=now;ATTACKS.values().removeIf(until->until<=now);}
  static void clear(){ATTACKS.clear();}
  public static void attackStarted(LivingEntity player,float length) {
    if(!Float.isFinite(length)||ATTACKS.size()>=128&&!ATTACKS.containsKey(player.getId()))return;
    ATTACKS.put(player.getId(),tick+Math.clamp((int)Math.ceil(length)+3,2,120));starts++;
  }
  public static void attackStopped(LivingEntity player,float fade) {
    if(ATTACKS.containsKey(player.getId()))ATTACKS.put(player.getId(),tick+Math.clamp((int)Math.ceil(fade),1,10));
  }
  public static boolean skillOwnsPose(LivingEntity player){var s=HeroVisuals.state(player.getId());return HeroVisuals.cue(player.getId())!=null||s!=null&&(s.flags()&(1|128|256|2048))!=0;}
  public static Object emptyPose() {
    try{return pose==null?null:pose.newInstance("","");}
    catch(ReflectiveOperationException error){fail(error);return null;}
  }
  static void skillStarted(int entityId) {
    var client=MinecraftClient.getInstance();var entity=client.world==null?null:client.world.getEntityById(entityId);
    if(stop==null||entity==null)return;
    try{stop.invoke(entity,2f);skillStops++;}catch(ReflectiveOperationException error){fail(error);}
  }
  private static boolean ownsAnimation(Object candidate) {
    if(!(candidate instanceof LivingEntity player))return false;
    boolean result=skillOwnsPose(player)||ATTACKS.getOrDefault(player.getId(),0L)>tick;
    // An idle weapon stance must not freeze EMF locomotion. Only attacks/skills own it.
    if(result)pausedFrames++;return result;
  }
  static com.google.gson.JsonObject observation() {
    var o=new com.google.gson.JsonObject();o.addProperty("loaded",loaded);o.addProperty("diagnostic",diagnostic);o.addProperty("emfHook",emfHook);
    o.addProperty("attackStarts",starts);o.addProperty("skillStops",skillStops);o.addProperty("pausedFrames",pausedFrames);o.addProperty("activeAttacks",ATTACKS.size());return o;
  }
}
