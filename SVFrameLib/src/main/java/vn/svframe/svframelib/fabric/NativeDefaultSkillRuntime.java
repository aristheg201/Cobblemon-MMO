package vn.svframe.svframelib.fabric;

import vn.svframe.svframelib.fabric.runtime.script.ScriptContext;
import vn.svframe.svframelib.fabric.runtime.script.ScriptPlatform;
import vn.svframe.svframelib.fabric.runtime.script.Vector3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Native Fabric execution backend for the bundled SVFrameLib 1.7.1 default:* skills. */
public final class NativeDefaultSkillRuntime {
    private static final ScriptPlatform PLATFORM = new FabricScriptPlatform();
    private static final Set<String> IDS = Set.of(
            "AMBERS","ARCANE_HAIL","ARCANE_RIFT","BACKSTAB","BLACK_HOLE","BLIND","BLINK","BLIZZARD","BLOODBATH",
            "BOUNCY_FIREBALL","BUNNY_MODE","BURN","BURNING_HANDS","CHICKEN_WRAITH","CIRCULAR_SLASH","COMBO_ATTACK","CONFUSE",
            "CONTAMINATION","CONTROL","CORROSION","CORRUPT","CORRUPTED_FANGS","CURSED_BEAM","DEATH_MARK","DEEP_WOUND",
            "EARTHQUAKE","EMPOWERED_ATTACK","EVADE","EXPLOSIVE_TURKEY","FIRE_BERSERKER","FIRE_METEOR","FIRE_RAGE","FIRE_STORM",
            "FIREBALL","FIREBOLT","FIREFLY","FREEZE","FREEZING_CURSE","FROG_MODE","FROZEN_AURA","FURTIVE_STRIKE","GRAND_HEAL",
            "GREATER_HEALINGS","HEAL","HEAVY_CHARGE","HOEARTHQUAKE","HOLY_MISSILE","HUMAN_SHIELD","ICE_CRYSTAL","ICE_SPIKES","IGNITE",
            "ITEM_BOMB","ITEM_THROW","LEAP","LIFE_ENDER","LIGHT_DASH","LIGHTNING_BEAM","MAGICAL_PATH","MAGICAL_SHIELD","MAGMA_FISSURE",
            "MINOR_EXPLOSION","MINOR_HEALINGS","NEPTUNE_GIFT","OVERLOAD","POISON","POWER_MARK","PRESENT_THROW","REGEN_ALLY","SHADOW_VEIL",
            "SHOCK","SHOCKWAVE","SHULKER_MISSILE","SKY_SMASH","SLOW","SMITE","SNEAKY_PICKY","SNOWMAN_TURRET","SPARKLE","STARFALL",
            "STUN","SWIFTNESS","TACTICAL_GRENADE","TARGETED_FIREBALL","TELEKINESY","THROW_UP","THRUST","TNT_THROW","VAMPIRISM",
            "VOID_ZAPPER","WARP","WEAKEN","WEAKEN_TARGET","WITHER");

    private NativeDefaultSkillRuntime() {}
    public static boolean supports(String id) { return IDS.contains(norm(id)); }
    public static Set<String> ids() { return IDS; }

    public static boolean cast(String id, ScriptContext ctx) {
        if (ctx == null || !supports(id)) return false;
        String key = norm(id);
        return switch (key) {
            case "AMBERS" -> ambers(ctx);
            case "BACKSTAB" -> backstab(ctx);
            case "FIRE_BERSERKER" -> fireBerserker(ctx);
            case "VAMPIRISM" -> vampirism(ctx);
            case "NEPTUNE_GIFT", "SNEAKY_PICKY" -> true;
            case "EMPOWERED_ATTACK" -> empoweredAttack(ctx);
            case "EVADE" -> evade(ctx);
            case "WEAKEN_TARGET" -> weakenTarget(ctx);
            case "BLIND" -> status(ctx, "minecraft:blindness", sec(ctx,"duration",5), 0, "minecraft:entity.warden.heartbeat");
            case "BLOODBATH" -> bloodbath(ctx);
            case "BURN" -> burn(ctx);
            case "COMBO_ATTACK" -> comboAttack(ctx);
            case "CONFUSE" -> confuse(ctx);
            case "CONTROL" -> control(ctx);
            case "DEATH_MARK" -> deathMark(ctx);
            case "DEEP_WOUND" -> deepWound(ctx);
            case "FIRE_STORM" -> fireStorm(ctx);
            case "FURTIVE_STRIKE" -> furtiveStrike(ctx);
            case "GREATER_HEALINGS" -> healTarget(ctx, p(ctx,"heal",10));
            case "HUMAN_SHIELD" -> humanShield(ctx);
            case "MAGMA_FISSURE" -> magmaFissure(ctx);
            case "MINOR_HEALINGS" -> healTarget(ctx, p(ctx,"heal",5));
            case "POISON" -> status(ctx,"minecraft:poison",sec(ctx,"duration",5),level(ctx,"amplifier",0),"minecraft:entity.spider.hurt");
            case "REGEN_ALLY" -> regenAlly(ctx);
            case "SHOCK" -> shock(ctx);
            case "SLOW" -> status(ctx,"minecraft:slowness",sec(ctx,"duration",4),level(ctx,"amplifier",0),"minecraft:block.glass.break");
            case "SMITE" -> smite(ctx);
            case "SPARKLE" -> sparkle(ctx);
            case "STARFALL" -> starfall(ctx);
            case "STUN" -> stun(ctx);
            case "TACTICAL_GRENADE" -> tacticalGrenade(ctx);
            case "TARGETED_FIREBALL" -> targetedFireball(ctx);
            case "TELEKINESY" -> telekinesy(ctx);
            case "WEAKEN" -> weaken(ctx);
            case "WITHER" -> status(ctx,"minecraft:wither",sec(ctx,"duration",5),level(ctx,"amplifier",0),"minecraft:entity.wither.hurt");
            case "BLINK" -> blink(ctx);
            case "BLIZZARD" -> blizzard(ctx);
            case "BUNNY_MODE" -> movementMode(ctx,true);
            case "BURNING_HANDS" -> burningHands(ctx);
            case "CHICKEN_WRAITH" -> chickenWraith(ctx);
            case "CIRCULAR_SLASH" -> circularSlash(ctx);
            case "FIRE_RAGE" -> fireRage(ctx);
            case "FIREBALL" -> fireball(ctx);
            case "FIREFLY" -> firefly(ctx);
            case "FROG_MODE" -> movementMode(ctx,false);
            case "FROZEN_AURA" -> frozenAura(ctx);
            case "GRAND_HEAL" -> grandHeal(ctx);
            case "HEAL" -> selfHeal(ctx);
            case "HOEARTHQUAKE" -> hoearthquake(ctx);
            case "LEAP" -> leap(ctx);
            case "LIGHT_DASH" -> lightDash(ctx);
            case "MAGICAL_PATH" -> magicalPath(ctx);
            case "MAGICAL_SHIELD" -> magicalShield(ctx);
            case "OVERLOAD" -> overload(ctx);
            case "PRESENT_THROW" -> presentThrow(ctx);
            case "SHADOW_VEIL" -> shadowVeil(ctx);
            case "SHOCKWAVE" -> shockwave(ctx);
            case "SKY_SMASH" -> skySmash(ctx);
            case "SWIFTNESS" -> swiftness(ctx);
            case "THROW_UP" -> throwUp(ctx);
            case "VOID_ZAPPER" -> voidZapper(ctx);
            case "ARCANE_HAIL" -> arcaneHail(ctx);
            case "BLACK_HOLE" -> blackHole(ctx);
            case "CONTAMINATION" -> contamination(ctx);
            case "CORROSION" -> corrosion(ctx);
            case "CORRUPT" -> corrupt(ctx);
            case "FREEZE" -> freeze(ctx);
            case "FREEZING_CURSE" -> freezingCurse(ctx);
            case "ICE_SPIKES" -> iceSpikes(ctx);
            case "IGNITE" -> ignite(ctx);
            case "LIFE_ENDER" -> lifeEnder(ctx);
            case "LIGHTNING_BEAM" -> lightningBeam(ctx);
            case "MINOR_EXPLOSION" -> minorExplosion(ctx);
            case "POWER_MARK" -> powerMark(ctx);
            case "SNOWMAN_TURRET" -> snowmanTurret(ctx);
            case "ARCANE_RIFT" -> arcaneRift(ctx);
            case "BOUNCY_FIREBALL" -> bouncyFireball(ctx);
            case "CORRUPTED_FANGS" -> corruptedFangs(ctx);
            case "CURSED_BEAM" -> cursedBeam(ctx);
            case "EARTHQUAKE" -> earthquake(ctx);
            case "EXPLOSIVE_TURKEY" -> explosiveTurkey(ctx);
            case "FIRE_METEOR" -> fireMeteor(ctx);
            case "FIREBOLT" -> firebolt(ctx);
            case "HEAVY_CHARGE" -> heavyCharge(ctx);
            case "HOLY_MISSILE" -> holyMissile(ctx);
            case "ICE_CRYSTAL" -> iceCrystal(ctx);
            case "SHULKER_MISSILE" -> shulkerMissile(ctx);
            case "TNT_THROW" -> tntThrow(ctx);
            case "THRUST" -> thrust(ctx);
            case "ITEM_BOMB" -> itemBomb(ctx);
            case "ITEM_THROW" -> itemThrow(ctx);
            case "WARP" -> warp(ctx);
            default -> false;
        };
    }

    private static boolean ambers(ScriptContext c) { if (ThreadLocalRandom.current().nextDouble(100d) < Math.max(0d,p(c,"percent",0))) PLATFORM.setOnFire(target(c),60); return true; }
    private static boolean backstab(ScriptContext c) { c.damage(c.damage()*(1d+p(c,"extra",0)/100d)); fx(target(c),"minecraft:crit",12); return true; }
    private static boolean fireBerserker(ScriptContext c) { if (PLATFORM.isOnFire(c.caster())) c.damage(c.damage()*(1d+p(c,"extra",0)/100d)); return true; }
    private static boolean vampirism(ScriptContext c) { PLATFORM.heal(c.caster(),Math.max(0d,c.damage())*p(c,"drain",0)/100d); fx(c.caster(),"minecraft:damage_indicator",8); return true; }
    private static boolean empoweredAttack(ScriptContext c) { c.damage(c.damage()*(1d+p(c,"extra",0)/100d)); double r=p(c,"radius",0); if(r>0) for(UUID id:near(c,PLATFORM.location(target(c)),r)) if(!id.equals(target(c))) hit(id,Math.max(0,c.damage()*p(c,"ratio",0)/100d)); return true; }
    private static boolean evade(ScriptContext c) { c.damage(0d); fx(c.caster(),"minecraft:cloud",18); return true; }
    private static boolean weakenTarget(ScriptContext c) { c.damage(c.damage()*Math.max(0d,1d-p(c,"extra-damage",0)/100d)); return true; }

    private static boolean status(ScriptContext c,String effect,int ticks,int amp,String sound){ UUID t=target(c); PLATFORM.potion(t,effect,Math.max(0,amp),Math.max(1,ticks),false,true,true); sound(t,sound,1f,1f); return true; }
    private static boolean bloodbath(ScriptContext c){ UUID t=target(c); hit(t,p(c,"amount",p(c,"damage",1))); fx(t,"minecraft:damage_indicator",24); sound(t,"minecraft:entity.player.attack.crit",1f,.8f); return true; }
    private static boolean burn(ScriptContext c){ UUID t=target(c); PLATFORM.setOnFire(t,sec(c,"duration",4)); fx(t,"minecraft:flame",24); sound(t,"minecraft:item.firecharge.use",1f,1f); return true; }
    private static boolean comboAttack(ScriptContext c){ UUID t=target(c); int count=Math.max(1,(int)Math.round(p(c,"count",3))); double damage=p(c,"damage",1); repeat(count,3,i->{hit(t,damage);fx(t,"minecraft:sweep_attack",1);}); return true; }
    private static boolean confuse(ScriptContext c){ UUID t=target(c); Vector3 pos=PLATFORM.location(t); double a=ThreadLocalRandom.current().nextDouble(Math.PI*2); PLATFORM.teleport(t,pos.add(new Vector3(Math.cos(a)*2,0,Math.sin(a)*2))); fx(t,"minecraft:portal",20); sound(t,"minecraft:entity.enderman.teleport",1f,1.3f); return true; }
    private static boolean control(ScriptContext c){ UUID t=target(c); PLATFORM.potion(t,"minecraft:slowness",Math.max(4,level(c,"knockback",4)),sec(c,"duration",3),false,true,true); PLATFORM.velocity(t,new Vector3(0,0,0)); sound(t,"minecraft:block.beacon.deactivate",1f,.7f); return true; }
    private static boolean deathMark(ScriptContext c){ UUID t=target(c); int duration=sec(c,"duration",5); PLATFORM.potion(t,"minecraft:glowing",0,duration,false,true,true); PLATFORM.potion(t,"minecraft:weakness",level(c,"amplifier",0),duration,false,true,true); repeat(Math.max(1,duration/20),20,i->hit(t,p(c,"damage",1))); return true; }
    private static boolean deepWound(ScriptContext c){ UUID t=target(c); hit(t,p(c,"damage",1)); int ticks=Math.max(1,(int)Math.round(p(c,"extra",0))); repeat(ticks,20,i->hit(t,1)); fx(t,"minecraft:damage_indicator",20); return true; }
    private static boolean fireStorm(ScriptContext c){ UUID t=target(c); double damage=p(c,"damage",1); int ignite=sec(c,"ignite",2); repeat(5,4,i->{hit(t,damage/5d);PLATFORM.setOnFire(t,ignite);fx(t,"minecraft:flame",8);}); return true; }
    private static boolean furtiveStrike(ScriptContext c){ UUID t=target(c); hit(t,p(c,"damage",1)*(1d+p(c,"extra",0)/100d)); double r=p(c,"radius",0); if(r>0) for(UUID id:near(c,PLATFORM.location(t),r)) if(!id.equals(t)) hit(id,p(c,"damage",1)); fx(t,"minecraft:crit",16); return true; }
    private static boolean healTarget(ScriptContext c,double amount){ UUID t=target(c); PLATFORM.heal(t,amount); fx(t,"minecraft:heart",8); sound(t,"minecraft:block.amethyst_block.chime",1f,1.2f); return true; }
    private static boolean humanShield(ScriptContext c){ UUID t=target(c); int d=sec(c,"duration",4); PLATFORM.potion(t,"minecraft:resistance",Math.max(0,level(c,"reduction",0)),d,false,true,true); PLATFORM.potion(c.caster(),"minecraft:resistance",Math.max(0,level(c,"redirect",0)),d,false,true,true); return true; }
    private static boolean magmaFissure(ScriptContext c){ UUID t=target(c); hit(t,p(c,"damage",1)); PLATFORM.setOnFire(t,sec(c,"ignite",3)); fxAt(PLATFORM.location(t),"minecraft:lava",20); return true; }
    private static boolean regenAlly(ScriptContext c){ UUID t=target(c); int dur=sec(c,"duration",5); double heal=p(c,"heal",1); repeat(Math.max(1,dur/20),20,i->PLATFORM.heal(t,heal)); return true; }
    private static boolean shock(ScriptContext c){ UUID t=target(c); PLATFORM.potion(t,"minecraft:slowness",4,sec(c,"duration",2),false,true,true); PLATFORM.entityEffect(t,"hurt"); fx(t,"minecraft:electric_spark",20); sound(t,"minecraft:entity.lightning_bolt.thunder",.6f,1.5f); return true; }
    private static boolean smite(ScriptContext c){ UUID t=target(c); PLATFORM.lightning(t,PLATFORM.location(t),true); hit(t,p(c,"damage",1)); return true; }
    private static boolean sparkle(ScriptContext c){ UUID t=target(c); double radius=p(c,"radius",3); int limit=Math.max(1,(int)Math.round(p(c,"limit",4))),done=0; for(UUID id:near(c,PLATFORM.location(t),radius)){hit(id,p(c,"damage",1));fx(id,"minecraft:end_rod",8);if(++done>=limit)break;} return true; }
    private static boolean starfall(ScriptContext c){ UUID t=target(c); repeat(5,3,i->{hit(t,p(c,"damage",1)/5d);fx(t,"minecraft:end_rod",6);}); sound(t,"minecraft:block.amethyst_cluster.break",1f,1.5f); return true; }
    private static boolean stun(ScriptContext c){ UUID t=target(c); PLATFORM.potion(t,"minecraft:slowness",10,sec(c,"duration",2),false,true,true); PLATFORM.potion(t,"minecraft:weakness",10,sec(c,"duration",2),false,true,true); PLATFORM.velocity(t,new Vector3(0,0,0)); sound(t,"minecraft:block.anvil.land",.8f,1.5f); return true; }
    private static boolean tacticalGrenade(ScriptContext c){ explode(c,PLATFORM.location(target(c)),p(c,"radius",3),p(c,"damage",1),p(c,"knock-up",.6),false); return true; }
    private static boolean targetedFireball(ScriptContext c){ UUID t=target(c); projectileTo(c,t,p(c,"damage",1),1.1,40,id->{PLATFORM.setOnFire(id,sec(c,"ignite",3));fx(id,"minecraft:flame",20);}); return true; }
    private static boolean telekinesy(ScriptContext c){ UUID t=target(c); Vector3 toCaster=PLATFORM.location(c.caster()).subtract(PLATFORM.location(t)).normalize().multiply(Math.max(.1,p(c,"knockback",1))); PLATFORM.velocity(t,toCaster.withY(.35)); repeat(Math.max(1,sec(c,"duration",1)/5),5,i->fx(t,"minecraft:witch",4)); return true; }
    private static boolean weaken(ScriptContext c){ PLATFORM.potion(target(c),"minecraft:weakness",Math.max(0,(int)Math.floor(p(c,"ratio",20)/20d)),sec(c,"duration",4),false,true,true); return true; }

    private static boolean blink(ScriptContext c){ UUID caster=c.caster(); double range=Math.max(1,p(c,"range",5)); Vector3 start=PLATFORM.location(caster); Vector3 end=start.add(PLATFORM.eyeDirection(caster).normalize().multiply(range)); fx(caster,"minecraft:instant_effect",32); sound(caster,"minecraft:entity.enderman.teleport",1f,1f); PLATFORM.teleport(caster,end); fx(caster,"minecraft:instant_effect",32); return true; }
    private static boolean blizzard(ScriptContext c){ int duration=sec(c,"duration",4); repeat(Math.max(1,duration/4),4,i->{for(UUID id:near(c,PLATFORM.location(c.caster()),6)){hit(id,p(c,"damage",1));PLATFORM.potion(id,"minecraft:slowness",1,20,false,true,true);}fx(c.caster(),"minecraft:snowflake",20);}); return true; }
    private static boolean movementMode(ScriptContext c,boolean bunny){ UUID caster=c.caster(); int d=sec(c,"duration",5); int jump=Math.max(0,level(c,"jump-force",1)),speed=Math.max(0,level(c,"speed",1)); PLATFORM.potion(caster,"minecraft:speed",speed,d,false,true,true); PLATFORM.potion(caster,"minecraft:jump_boost",jump,d,false,true,true); if(bunny) PLATFORM.potion(caster,"minecraft:regeneration",0,d,false,false,true); return true; }
    private static boolean burningHands(ScriptContext c){ int d=sec(c,"duration",5); repeat(Math.max(1,d/10),10,i->{Vector3 origin=PLATFORM.location(c.caster()),dir=PLATFORM.eyeDirection(c.caster()).normalize();for(UUID id:near(c,origin.add(dir.multiply(2)),2.5)){hit(id,p(c,"damage",1)/2d);PLATFORM.setOnFire(id,30);}fx(c.caster(),"minecraft:flame",12);}); return true; }
    private static boolean chickenWraith(ScriptContext c){ int d=sec(c,"duration",4); repeat(Math.max(1,d/5),5,i->projectileForward(c,p(c,"damage",1),Math.max(.5,p(c,"force",1)),20,id->fx(id,"minecraft:poof",12))); return true; }
    private static boolean circularSlash(ScriptContext c){ Vector3 center=PLATFORM.location(c.caster()); double damage=p(c,"damage",1),r=p(c,"radius",3),kb=p(c,"knockback",.5); for(UUID id:near(c,center,r)){hit(id,damage);knock(center,id,kb,.15);}fxAt(center,"minecraft:sweep_attack",12);sound(c.caster(),"minecraft:entity.player.attack.sweep",1f,1f);return true; }
    private static boolean fireRage(ScriptContext c){ int count=Math.max(1,(int)Math.round(p(c,"count",5))),dur=sec(c,"duration",4); repeat(count,Math.max(1,dur/count),i->{Collection<UUID> n=PLATFORM.nearby(c.caster(),6,4);if(!n.isEmpty()){UUID t=new ArrayList<>(n).get(ThreadLocalRandom.current().nextInt(n.size()));hit(t,p(c,"damage",1));PLATFORM.setOnFire(t,sec(c,"ignite",2));fx(t,"minecraft:flame",10);}});return true; }
    private static boolean fireball(ScriptContext c){ double damage=p(c,"damage",1),ratio=p(c,"ratio",50)/100d; projectileForward(c,damage,1.1,50,id->{PLATFORM.setOnFire(id,sec(c,"ignite",3));Vector3 center=PLATFORM.location(id);repeat(3,20,k->explode(c,center,2.2,damage*ratio/3d,.2,true));});return true; }
    private static boolean firefly(ScriptContext c){ UUID caster=c.caster(); int dur=sec(c,"duration",4); PLATFORM.potion(caster,"minecraft:levitation",0,dur,false,true,true); repeat(Math.max(1,dur/5),5,i->{for(UUID id:PLATFORM.nearby(caster,2.5,2.5)){hit(id,p(c,"damage",1));knock(PLATFORM.location(caster),id,p(c,"knockback",.2),.1);}fx(caster,"minecraft:flame",10);});return true; }
    private static boolean frozenAura(ScriptContext c){ int dur=sec(c,"duration",5),amp=level(c,"amplifier",1); double radius=p(c,"radius",4); repeat(Math.max(1,dur/10),10,i->{for(UUID id:PLATFORM.nearby(c.caster(),radius,radius))PLATFORM.potion(id,"minecraft:slowness",amp,20,false,true,true);fx(c.caster(),"minecraft:snowflake",12);});return true; }
    private static boolean grandHeal(ScriptContext c){ double radius=p(c,"radius",5),heal=p(c,"heal",10); PLATFORM.heal(c.caster(),heal); for(UUID id:PLATFORM.nearby(c.caster(),radius,radius))PLATFORM.heal(id,heal);fx(c.caster(),"minecraft:heart",16);return true; }
    private static boolean selfHeal(ScriptContext c){ PLATFORM.heal(c.caster(),p(c,"heal",5));fx(c.caster(),"minecraft:heart",10);sound(c.caster(),"minecraft:block.amethyst_block.chime",1f,1.2f);return true; }
    private static boolean hoearthquake(ScriptContext c){ Vector3 center=PLATFORM.location(c.caster()); repeat(6,4,i->{for(UUID id:near(c,center,2+i*.7))PLATFORM.velocity(id,new Vector3(0,.25,0));fxAt(center,"minecraft:cloud",8);});return true; }
    private static boolean leap(ScriptContext c){ UUID caster=c.caster(); Vector3 dir=PLATFORM.eyeDirection(caster).normalize(); PLATFORM.velocity(caster,dir.multiply(p(c,"force",1.4)).withY(Math.max(.5,p(c,"force",1.4)*.55)));fx(caster,"minecraft:cloud",12);sound(caster,"minecraft:entity.ender_dragon.flap",.8f,1.2f);return true; }
    private static boolean lightDash(ScriptContext c){ UUID caster=c.caster(); double length=p(c,"length",8),damage=p(c,"damage",1); Vector3 start=PLATFORM.location(caster),dir=PLATFORM.eyeDirection(caster).normalize(); int steps=Math.max(2,(int)Math.ceil(length)); for(int i=1;i<=steps;i++){Vector3 point=start.add(dir.multiply((double)i/steps*length));for(UUID id:near(c,point,1.3))hit(id,damage/Math.max(1,steps/3d));fxAt(point,"minecraft:end_rod",2);}PLATFORM.teleport(caster,start.add(dir.multiply(length)));return true; }
    private static boolean magicalPath(ScriptContext c){ int dur=sec(c,"duration",5); PLATFORM.potion(c.caster(),"minecraft:speed",1,dur,false,true,true); repeat(Math.max(1,dur/5),5,i->fx(c.caster(),"minecraft:enchant",8));return true; }
    private static boolean magicalShield(ScriptContext c){ int d=sec(c,"duration",5),amp=Math.max(0,(int)Math.round(p(c,"power",20)/20d)); PLATFORM.potion(c.caster(),"minecraft:resistance",amp,d,false,true,true); repeat(Math.max(1,d/10),10,i->fx(c.caster(),"minecraft:enchanted_hit",8));return true; }
    private static boolean overload(ScriptContext c){ Vector3 center=PLATFORM.location(c.caster()); double r=p(c,"radius",4),d=p(c,"damage",1); for(UUID id:near(c,center,r)){hit(id,d);PLATFORM.potion(id,"minecraft:slowness",1,30,false,true,true);}PLATFORM.lightning(c.caster(),center,true);fxAt(center,"minecraft:electric_spark",30);return true; }
    private static boolean presentThrow(ScriptContext c){ double damage=p(c,"damage",1),radius=p(c,"radius",3); projectileForward(c,damage,Math.max(.4,p(c,"force",1)),40,id->explode(c,PLATFORM.location(id),radius,damage,.4,false));return true; }
    private static boolean shadowVeil(ScriptContext c){ int d=sec(c,"duration",5); PLATFORM.potion(c.caster(),"minecraft:invisibility",0,d,false,false,true); PLATFORM.potion(c.caster(),"minecraft:speed",Math.max(0,level(c,"deception",0)),d,false,false,true);sound(c.caster(),"minecraft:entity.enderman.teleport",.7f,.6f);return true; }
    private static boolean shockwave(ScriptContext c){ double length=p(c,"length",8),up=p(c,"knock-up",.5); Vector3 start=PLATFORM.location(c.caster()),dir=PLATFORM.eyeDirection(c.caster()).normalize(); int steps=Math.max(2,(int)Math.ceil(length)); repeat(steps,1,i->{Vector3 point=start.add(dir.multiply(i+1));for(UUID id:near(c,point,1.5))PLATFORM.velocity(id,new Vector3(0,up,0));fxAt(point,"minecraft:cloud",6);});return true; }
    private static boolean skySmash(ScriptContext c){ Vector3 center=PLATFORM.location(c.caster()); double up=p(c,"knock-up",.8),d=p(c,"damage",1); for(UUID id:PLATFORM.nearby(c.caster(),5,4)){hit(id,d);PLATFORM.velocity(id,new Vector3(0,up,0));}fxAt(center,"minecraft:explosion",2);sound(c.caster(),"minecraft:entity.generic.explode",1f,.8f);return true; }
    private static boolean swiftness(ScriptContext c){ int d=sec(c,"duration",5),amp=level(c,"amplifier",1);PLATFORM.potion(c.caster(),"minecraft:speed",amp,d,false,true,true);PLATFORM.potion(c.caster(),"minecraft:jump_boost",amp,d,false,true,true);fx(c.caster(),"minecraft:instant_effect",24);return true; }
    private static boolean throwUp(ScriptContext c){ int d=sec(c,"duration",2); double damage=p(c,"damage",1); for(UUID id:PLATFORM.nearby(c.caster(),4,4)){PLATFORM.velocity(id,new Vector3(0,.9,0));hit(id,damage);SVFrameLibFabricMod.schedule(Math.max(1,d),()->hit(id,damage));}return true; }
    private static boolean voidZapper(ScriptContext c){ UUID caster=c.caster(); double len=p(c,"length",10),damage=p(c,"damage",1),max=Math.max(1,p(c,"max",3)),extra=p(c,"extra",0)/100d,kb=p(c,"knockback",.4); Vector3 start=PLATFORM.location(caster),dir=PLATFORM.eyeDirection(caster).normalize(); int hits=0; for(int i=1;i<=(int)Math.ceil(len)&&hits<max;i++){Vector3 point=start.add(dir.multiply(i));for(UUID id:near(c,point,1.2)){hit(id,damage*(1+extra*hits));knock(start,id,kb,.1);if(++hits>=max)break;}fxAt(point,"minecraft:portal",3);}return true; }

    private static boolean arcaneHail(ScriptContext c){ Vector3 center=center(c); int dur=sec(c,"duration",4); double r=p(c,"radius",4),d=p(c,"damage",1); repeat(Math.max(1,dur/5),5,i->{for(UUID id:near(c,center,r))hit(id,d);fxAt(center,"minecraft:enchant",18);});return true; }
    private static boolean blackHole(ScriptContext c){ Vector3 center=center(c); int dur=sec(c,"duration",4); double r=p(c,"radius",4); repeat(Math.max(1,dur/2),2,i->{for(UUID id:near(c,center,r)){Vector3 pull=center.subtract(PLATFORM.location(id));double distance=Math.max(.5,pull.length());PLATFORM.velocity(id,pull.normalize().multiply(Math.min(.8,.18*distance)).withY(.08));}fxAt(center,"minecraft:reverse_portal",20);});sound(c.caster(),"minecraft:block.portal.trigger",.7f,.5f);return true; }
    private static boolean contamination(ScriptContext c){ Vector3 center=center(c); int dur=sec(c,"duration",5); double r=p(c,"radius",4),d=p(c,"damage",1); repeat(Math.max(1,dur/10),10,i->{for(UUID id:near(c,center,r))hit(id,d);fxAt(center,"minecraft:spore_blossom_air",12);});return true; }
    private static boolean corrosion(ScriptContext c){ Vector3 center=center(c); int dur=sec(c,"duration",5),amp=level(c,"amplifier",0); double r=p(c,"radius",4); for(UUID id:near(c,center,r))PLATFORM.potion(id,"minecraft:weakness",amp,dur,false,true,true);fxAt(center,"minecraft:falling_honey",20);return true; }
    private static boolean corrupt(ScriptContext c){ Vector3 center=center(c); int dur=sec(c,"duration",5),amp=level(c,"amplifier",0); double r=p(c,"radius",4),d=p(c,"damage",1); repeat(Math.max(1,dur/10),10,i->{for(UUID id:near(c,center,r)){hit(id,d);PLATFORM.potion(id,"minecraft:wither",amp,20,false,true,true);}fxAt(center,"minecraft:witch",15);});return true; }
    private static boolean freeze(ScriptContext c){ Vector3 center=center(c); int dur=sec(c,"duration",4),amp=level(c,"amplifier",1);double r=p(c,"radius",4);for(UUID id:near(c,center,r)){PLATFORM.potion(id,"minecraft:slowness",amp,dur,false,true,true);PLATFORM.velocity(id,new Vector3(0,0,0));}fxAt(center,"minecraft:snowflake",30);return true; }
    private static boolean freezingCurse(ScriptContext c){ Vector3 center=center(c); int dur=sec(c,"duration",4),amp=level(c,"amplifier",1);double r=p(c,"radius",4),d=p(c,"damage",1);SVFrameLibFabricMod.schedule(12,()->{for(UUID id:near(c,center,r)){hit(id,d);PLATFORM.potion(id,"minecraft:slowness",amp,dur,false,true,true);}fxAt(center,"minecraft:snowflake",35);});return true; }
    private static boolean iceSpikes(ScriptContext c){ Vector3 center=center(c);double d=p(c,"damage",1),slow=p(c,"slow",1);for(UUID id:near(c,center,3)){hit(id,d);PLATFORM.potion(id,"minecraft:slowness",Math.max(0,(int)Math.round(slow)),40,false,true,true);}fxAt(center,"minecraft:snowflake",25);return true; }
    private static boolean ignite(ScriptContext c){ Vector3 center=center(c);double r=p(c,"radius",4);int fire=Math.min(sec(c,"duration",4),sec(c,"max-ignite",10));for(UUID id:near(c,center,r))PLATFORM.setOnFire(id,fire);fxAt(center,"minecraft:flame",30);return true; }
    private static boolean lifeEnder(ScriptContext c){ Vector3 center=center(c);explode(c,center,p(c,"radius",4),p(c,"damage",1),p(c,"knockback",.6),false);fxAt(center,"minecraft:soul",30);return true; }
    private static boolean lightningBeam(ScriptContext c){ Vector3 center=center(c); double r=p(c,"radius",3),d=p(c,"damage",1); PLATFORM.lightning(c.caster(),center,true);for(UUID id:near(c,center,r))hit(id,d);fxAt(center,"minecraft:electric_spark",25);return true; }
    private static boolean minorExplosion(ScriptContext c){ explode(c,center(c),p(c,"radius",3),p(c,"damage",1),p(c,"knockback",.5),true);return true; }
    private static boolean powerMark(ScriptContext c){ Vector3 center=center(c); int d=sec(c,"duration",5); double ratio=p(c,"ratio",20)/100d; repeat(Math.max(1,d/10),10,i->{for(UUID id:near(c,center,4))hit(id,Math.max(0,p(c,"damage",1)*ratio));fxAt(center,"minecraft:enchant",8);}); return true; }
    private static boolean snowmanTurret(ScriptContext c){ Vector3 center=center(c); int d=sec(c,"duration",5); double radius=p(c,"radius",8),damage=p(c,"damage",1); repeat(Math.max(1,d/10),10,i->{List<UUID> list=new ArrayList<>(near(c,center,radius));if(!list.isEmpty()){UUID t=list.get(ThreadLocalRandom.current().nextInt(list.size()));projectileFromTo(c,center,PLATFORM.location(t),damage,.9,30,id->PLATFORM.potion(id,"minecraft:slowness",0,20,false,true,true));}});return true; }

    private static boolean arcaneRift(ScriptContext c){ int d=sec(c,"duration",4),amp=level(c,"amplifier",0);double damage=p(c,"damage",1),speed=Math.max(.2,p(c,"speed",1));repeat(Math.max(1,d/5),5,i->projectileForward(c,damage,speed,30,id->PLATFORM.potion(id,"minecraft:weakness",amp,30,false,true,true)));return true; }
    private static boolean bouncyFireball(ScriptContext c){ double d=p(c,"damage",1),r=p(c,"radius",3),speed=Math.max(.2,p(c,"speed",1));projectileForward(c,d,speed,50,id->{PLATFORM.setOnFire(id,sec(c,"ignite",3));explode(c,PLATFORM.location(id),r,d,.3,true);});return true; }
    private static boolean corruptedFangs(ScriptContext c){ int f=Math.max(1,(int)Math.round(p(c,"fangs",5)));Vector3 start=PLATFORM.location(c.caster()),dir=PLATFORM.eyeDirection(c.caster()).normalize();repeat(f,2,i->{Vector3 point=start.add(dir.multiply(i+2));for(UUID id:near(c,point,1.2))hit(id,p(c,"damage",1));fxAt(point,"minecraft:crit",6);});return true; }
    private static boolean cursedBeam(ScriptContext c){ int dur=sec(c,"duration",3);double damage=p(c,"damage",1);repeat(Math.max(1,dur/2),2,i->beam(c,damage,12,"minecraft:witch"));return true; }
    private static boolean earthquake(ScriptContext c){ int dur=sec(c,"duration",4),amp=level(c,"amplifier",0);double damage=p(c,"damage",1);Vector3 center=PLATFORM.location(c.caster());repeat(Math.max(1,dur/5),5,i->{for(UUID id:near(c,center,5)){hit(id,damage);PLATFORM.potion(id,"minecraft:slowness",amp,15,false,true,true);}fxAt(center,"minecraft:cloud",20);});return true; }
    private static boolean explosiveTurkey(ScriptContext c){ double damage=p(c,"damage",1),r=p(c,"radius",4),kb=p(c,"knockback",.6);int d=sec(c,"duration",3);Vector3 point=PLATFORM.location(c.caster()).add(PLATFORM.eyeDirection(c.caster()).normalize().multiply(3));SVFrameLibFabricMod.schedule(d,()->explode(c,point,r,damage,kb,true));return true; }
    private static boolean fireMeteor(ScriptContext c){ Vector3 impact=center(c),origin=impact.add(new Vector3(0,10,0));double damage=p(c,"damage",1),r=p(c,"radius",4),kb=p(c,"knockback",.6);projectileFromTo(c,origin,impact,damage,.8,50,id->explode(c,PLATFORM.location(id),r,damage,kb,true));return true; }
    private static boolean firebolt(ScriptContext c){ projectileForward(c,p(c,"damage",1),1.5,35,id->{PLATFORM.setOnFire(id,sec(c,"ignite",2));fx(id,"minecraft:flame",10);});return true; }
    private static boolean heavyCharge(ScriptContext c){ UUID caster=c.caster();Vector3 dir=PLATFORM.eyeDirection(caster).normalize();PLATFORM.velocity(caster,dir.multiply(1.4));repeat(8,1,i->{Vector3 point=PLATFORM.location(caster);for(UUID id:near(c,point,1.5)){hit(id,p(c,"damage",1));knock(point,id,p(c,"knockback",.7),.2);}fxAt(point,"minecraft:cloud",5);});return true; }
    private static boolean holyMissile(ScriptContext c){ double d=p(c,"damage",1);UUID t=target(c);projectileTo(c,t,d,.75,Math.max(20,sec(c,"duration",4)/2),id->fx(id,"minecraft:end_rod",18));return true; }
    private static boolean iceCrystal(ScriptContext c){ double d=p(c,"damage",1);int dur=sec(c,"duration",4),amp=level(c,"amplifier",1);projectileForward(c,d,.9,35,id->{PLATFORM.potion(id,"minecraft:slowness",amp,dur,false,true,true);fx(id,"minecraft:snowflake",18);});return true; }
    private static boolean shulkerMissile(ScriptContext c){ UUID t=target(c);PLATFORM.shulkerBullet(c.caster(),t,p(c,"damage",1));SVFrameLibFabricMod.schedule(10,()->PLATFORM.potion(t,"minecraft:levitation",0,sec(c,"effect-duration",2),false,true,true));return true; }
    private static boolean tntThrow(ScriptContext c){ Vector3 point=PLATFORM.location(c.caster()).add(PLATFORM.eyeDirection(c.caster()).normalize().multiply(Math.max(.2,p(c,"force",1))*5));SVFrameLibFabricMod.schedule(40,()->explode(c,point,4,6,.7,true));fxAt(point,"minecraft:smoke",10);return true; }
    private static boolean thrust(ScriptContext c){ beam(c,p(c,"damage",1),6,"minecraft:sweep_attack");PLATFORM.velocity(c.caster(),PLATFORM.eyeDirection(c.caster()).normalize().multiply(1.2));return true; }
    private static boolean itemBomb(ScriptContext c){ projectileForward(c,p(c,"damage",1),1,30,id->{Vector3 center=PLATFORM.location(id);explode(c,center,p(c,"radius",3),p(c,"damage",1),.4,true);for(UUID x:near(c,center,p(c,"radius",3)))PLATFORM.potion(x,"minecraft:slowness",level(c,"slow-amplifier",0),sec(c,"slow-duration",2),false,true,true);});return true; }
    private static boolean itemThrow(ScriptContext c){ projectileForward(c,p(c,"damage",1),Math.max(.2,p(c,"force",1)),30,id->fx(id,"minecraft:crit",6));return true; }
    private static boolean warp(ScriptContext c){ UUID caster=c.caster();double range=Math.max(1,p(c,"range",8));Vector3 start=PLATFORM.location(caster),end=start.add(PLATFORM.eyeDirection(caster).normalize().multiply(range));PLATFORM.teleport(caster,end);fx(caster,"minecraft:portal",30);sound(caster,"minecraft:entity.enderman.teleport",1f,.8f);return true; }

    private static UUID target(ScriptContext c){ return c.target()==null?c.caster():c.target(); }
    private static String norm(String id){ return id==null?"":id.trim().toUpperCase(Locale.ROOT).replace('-','_').replace(' ','_'); }
    private static double p(ScriptContext c,String key,double fallback){ Double v=c.numbers().get(key);if(v==null)v=c.numbers().get("parameter."+key);if(v!=null)return v;Object o=c.objects().get(key);if(o==null)o=c.objects().get("parameter."+key);if(o instanceof Number n)return n.doubleValue();try{return o==null?fallback:Double.parseDouble(String.valueOf(o));}catch(RuntimeException ignored){return fallback;} }
    private static int sec(ScriptContext c,String key,double fallbackSeconds){ return Math.max(1,(int)Math.round(p(c,key,fallbackSeconds)*20d)); }
    private static int level(ScriptContext c,String key,int fallback){ return Math.max(0,(int)Math.round(p(c,key,fallback))); }
    private static Vector3 center(ScriptContext c){ return c.targetLocation()!=null?c.targetLocation():PLATFORM.location(target(c)); }
    private static void hit(UUID id,double amount){ if(amount>0) PLATFORM.damage(id,amount,"SKILL"); }
    private static void fx(UUID id,String particle,int amount){ PLATFORM.particle(id,particle,amount,.35,.45,.35,.04); }
    private static void fxAt(Vector3 p,String particle,int amount){ PLATFORM.particleAt(p,particle,amount,.4,.4,.4,.04); }
    private static void sound(UUID id,String sound,float volume,float pitch){ try{PLATFORM.sound(id,sound,volume,pitch);}catch(RuntimeException ignored){} }
    private static Collection<UUID> near(ScriptContext c,Vector3 point,double radius){ double dist=PLATFORM.location(c.caster()).subtract(point).length(); Collection<UUID> candidates=PLATFORM.nearby(c.caster(),Math.max(radius,dist+radius),Math.max(radius,dist+radius)); Set<UUID> out=new HashSet<>(); for(UUID id:candidates)if(PLATFORM.location(id).subtract(point).length()<=radius)out.add(id);if(!target(c).equals(c.caster())&&PLATFORM.location(target(c)).subtract(point).length()<=radius)out.add(target(c));out.remove(c.caster());return out; }
    private static void knock(Vector3 center,UUID id,double horizontal,double up){ Vector3 d=PLATFORM.location(id).subtract(center);PLATFORM.velocity(id,d.length()<1e-6?new Vector3(0,up,0):d.normalize().multiply(horizontal).withY(up)); }
    private static void explode(ScriptContext c,Vector3 center,double radius,double damage,double knockback,boolean effects){ for(UUID id:near(c,center,Math.max(.5,radius))){hit(id,damage);if(knockback!=0)knock(center,id,knockback,Math.max(.15,knockback*.35));}if(effects){fxAt(center,"minecraft:explosion",2);sound(c.caster(),"minecraft:entity.generic.explode",1f,1f);} }
    private static void repeat(int count,int interval,Consumer<Integer> task){ if(count<=0)return;task.accept(0);for(int i=1;i<count;i++){int n=i;SVFrameLibFabricMod.schedule(Math.max(1,interval*i),()->task.accept(n));} }
    private static void projectileForward(ScriptContext c,double damage,double speed,double range,Consumer<UUID> after){ Vector3 origin=PLATFORM.location(c.caster()).add(new Vector3(0,1.4,0));projectile(c,origin,PLATFORM.eyeDirection(c.caster()).normalize(),damage,speed,range,after); }
    private static void projectileTo(ScriptContext c,UUID target,double damage,double speed,double range,Consumer<UUID> after){ Vector3 origin=PLATFORM.location(c.caster()).add(new Vector3(0,1.4,0));projectile(c,origin,PLATFORM.location(target).add(new Vector3(0,1,0)).subtract(origin).normalize(),damage,speed,range,after); }
    private static void projectileFromTo(ScriptContext c,Vector3 origin,Vector3 target,double damage,double speed,double range,Consumer<UUID> after){ projectile(c,origin,target.subtract(origin).normalize(),damage,speed,range,after); }
    private static void projectile(ScriptContext c,Vector3 origin,Vector3 dir,double damage,double speed,double range,Consumer<UUID> after){ int life=Math.max(10,(int)Math.ceil(range/Math.max(.05,speed)));PLATFORM.projectile(new ScriptPlatform.ProjectileSpec(origin,dir,Math.max(.05,speed),Math.max(1,range),.45,life),p->fxAt(p,"minecraft:crit",1),id->{if(!id.equals(c.caster())){hit(id,damage);after.accept(id);}},()->{}); }
    private static void beam(ScriptContext c,double damage,double length,String particle){ Vector3 start=PLATFORM.location(c.caster()).add(new Vector3(0,1.2,0)),dir=PLATFORM.eyeDirection(c.caster()).normalize();int steps=Math.max(1,(int)Math.ceil(length*2));Set<UUID> hits=new HashSet<>();for(int i=1;i<=steps;i++){Vector3 point=start.add(dir.multiply(i*.5));fxAt(point,particle,1);for(UUID id:near(c,point,.8))if(hits.add(id))hit(id,damage);} }
}
