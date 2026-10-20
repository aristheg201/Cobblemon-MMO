package vn.svframe.fantasyhub.core.hero;
import java.util.*;
import java.util.function.Consumer;

/** Explicit executable registrations, independent from the configurable catalog. */
public final class HeroSkillRouter {
  private static final Map<String,Consumer<HeroRuntime.Cast>> ROUTES=new LinkedHashMap<>();
  static {
    add("SPIDER_MAN",SpiderSkills::cast,"WEB_BLITZ","WEB_SWING","WEB_SHOOTER_BARRAGE","COCOON_SLAM","AIR_COMBO","WEB_SHIELD","WORLD_WEB_TRAP");
    add("SORCERER_SUPREME",SorcererSkills::cast,"ELDRITCH_WHIP","TAO_MANDALAS","MIRROR_DIMENSION","ASTRAL_PROJECTION","SLING_RING","IMAGES_OF_IKONN","EYE_OF_AGAMOTTO");
    add("IRON_MAN",IronSkills::cast,"REPULSOR_BLAST","MICRO_MISSILES","TARGETING_LASERS","THRUSTER_DASH","UNIBEAM","HOUSE_PARTY","ORBITAL_STRIKE");
    add("THOR",ThorSkills::cast,"MJOLNIR_THROW","THUNDEROUS_SLAM","LIGHTNING_SPIN","STORMBREAKER","BIFROST","GOD_OF_THUNDER","BRING_ME_THANOS");
    add("FLASH",FlashSkills::cast,"SUPER_SPEED","SLOW_MOTION","LIGHTNING_THROW","WIND_FUNNEL","MOLECULAR_PHASING","SPEED_SIPHON","INFINITE_MASS_PUNCH");
    add("BATMAN",BatmanSkills::cast,"BATARANG","BATCLAW","SMOKE_TAKEDOWN","DETECTIVE_VISION","DISRUPTOR","CAPE_COMBO","BATWING_STRIKE");
  }
  private static void add(String hero,Consumer<HeroRuntime.Cast> route,String...keys){for(String key:keys)if(ROUTES.put(hero+"_"+key,route)!=null)throw new IllegalStateException("Duplicate hero handler");}
  public static Set<String> abilities(){return Set.copyOf(ROUTES.keySet());}
  static void execute(HeroRuntime.Cast c){var route=ROUTES.get(c.ability.id());if(route==null)throw new IllegalArgumentException("Missing executable skill "+c.ability.id());route.accept(c);}
  static boolean requiresTarget(String id){return Set.of("SPIDER_MAN_COCOON_SLAM","SPIDER_MAN_AIR_COMBO","SORCERER_SUPREME_ELDRITCH_WHIP","FLASH_LIGHTNING_THROW","BATMAN_DISRUPTOR").contains(id);}
  static boolean mobility(String id){return Set.of("SPIDER_MAN_WEB_BLITZ","SPIDER_MAN_WEB_SWING","SPIDER_MAN_AIR_COMBO","SORCERER_SUPREME_ASTRAL_PROJECTION","SORCERER_SUPREME_SLING_RING","IRON_MAN_THRUSTER_DASH","THOR_THUNDEROUS_SLAM","FLASH_MOLECULAR_PHASING","BATMAN_BATCLAW").contains(id);}
}
