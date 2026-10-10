package vn.svframe.svframelib.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.PlayerMetadata;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.DamageMetadata;
import vn.svframe.svframelib.damage.DamageType;

import java.util.*;

/** Native class behaviors ported from the supplied Death Knight YAML and AMK v17 Skript.
 * This is an explicit class adapter, not an interpreter for arbitrary MythicMobs/Skript files. */
public final class ReferenceClassSkillRuntime {
    private static final Set<String> DEATH=Set.of("CURSED_SEAL","DEATH_STRIKE_ST","PHANTOM_CHARGE","WRAITHBOUND_CHAINS","SOUL_BARRIER","NECROTIC_WHIRLWIND","DEATH_SENTENCE");
    private static final Set<String> AMK=Set.of("PASSIVE","NORTHERN_GUST","CLAP","HEROIC_SURGE","OVERLOAD","HIT_ME","METEOR_LANDING");
    private static final Map<ServerPlayerEntity,State> STATES=new IdentityHashMap<>();
    private static final Map<LivingEntity,Mark> MARKS=new IdentityHashMap<>();
    private static final ThreadLocal<Boolean> OWN_DAMAGE=ThreadLocal.withInitial(()->false);
    private static final ParticleEffect SOUL=new DustColorTransitionParticleEffect(new Vector3f(.635f,.973f,.796f),new Vector3f(.055f,.686f,.608f),.8f);
    private ReferenceClassSkillRuntime() { }
    public static boolean supports(String id) { String key=normalize(id);return key.startsWith("DEATH_KNIGHT_")?DEATH.contains(key.substring(13)):key.startsWith("ANTI_MAGE_KNIGHT_")&&AMK.contains(key.substring(17)); }
    public static String normalize(String id) { String key=id==null?"":id.toUpperCase(Locale.ROOT);return key.startsWith("CLS_")?key.substring(4):key; }
    public static void install() {
        ServerTickEvents.END_SERVER_TICK.register(server->{
            long now=SVFrameLibFabricMod.currentTick();
            STATES.entrySet().removeIf(e->!valid(e.getKey(),e.getValue().world));
            MARKS.entrySet().removeIf(e->!e.getKey().isAlive()||e.getKey().isRemoved()||now>=e.getValue().expires);
            for (var entry:STATES.entrySet()) {
                var player=entry.getKey();var state=entry.getValue();
                if (state.guardUntil>now && now%5==0) {
                    ring(player.getServerWorld(),player.getPos().add(0,1,0),2.2,SOUL);
                    for (LivingEntity target:targets(player,player.getPos(),10)) if (target instanceof MobEntity mob) mob.setTarget(player);
                } else if(state.guardUntil>0 && now>=state.guardUntil) {
                    state.guardUntil=0;
                    double damage=6+Math.min(player.getMaxHealth(),state.stored)*.25;state.stored=0;
                    for(LivingEntity target:targets(player,player.getPos(),10)) if(hit(player,target,damage)) velocity(target,away(player,target,.7,.2));
                    ring(player.getServerWorld(),player.getPos(),10,ParticleTypes.END_ROD);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{STATES.clear();MARKS.clear();});
    }
    private static State state(ServerPlayerEntity player) { return STATES.computeIfAbsent(player,p->new State(p.getServerWorld())); }
    public static boolean canCast(String id,ServerPlayerEntity player) {
        if(!supports(id)||player==null||!player.isAlive()||player.isSpectator())return false;
        String key=normalize(id);
        if(key.endsWith("DEATH_SENTENCE")||key.endsWith("METEOR_LANDING")||key.endsWith("HIT_ME"))return HeroEntranceRuntime.canStart(player);
        if(key.endsWith("PHANTOM_CHARGE")||key.endsWith("HEROIC_SURGE"))return player.isOnGround()&&state(player).busyUntil<=SVFrameLibFabricMod.currentTick();
        if(key.endsWith("OVERLOAD"))return state(player).guardUntil<=SVFrameLibFabricMod.currentTick();
        return true;
    }
    public static boolean cast(String id,ServerPlayerEntity player,Map<String,?> parameters) {
        if(!canCast(id,player))return false;
        String key=normalize(id);State state=state(player);long now=SVFrameLibFabricMod.currentTick();
        switch(key) {
            case "DEATH_KNIGHT_CURSED_SEAL" -> state.curseUntil=now+40;
            case "ANTI_MAGE_KNIGHT_PASSIVE" -> state.amkUntil=now+40;
            case "DEATH_KNIGHT_DEATH_SENTENCE","ANTI_MAGE_KNIGHT_HIT_ME","ANTI_MAGE_KNIGHT_METEOR_LANDING" -> {
                Map<String,Object> options=new LinkedHashMap<>(parameters);
                options.put("range",positive(parameters,"range",key.startsWith("DEATH")?32:22));
                options.put("radius",positive(parameters,"radius",key.startsWith("DEATH")?7:10));
                options.put("damage",positive(parameters,"damage",key.startsWith("DEATH")?8:10));
                // AMK v17 combat uses the script's meteor damage, not unused wrapper modifiers.
                if(key.startsWith("ANTI"))options.put("damage",10d);
                if(key.startsWith("DEATH"))options.put("cursed_execution",true);
                if(!HeroEntranceRuntime.start(player,options))return false;
                if(key.startsWith("DEATH")){
                    NativeVisualRuntime.spawn("vfx_death_wings_1",player,player.getPos().add(0,1.5,0),100,true);
                    SVFrameLibFabricMod.schedule(12,()->{if(valid(player,state.world)&&HeroEntranceRuntime.view(player)!=null) NativeVisualRuntime.spawn("vfx_soul_blade",player,player.getPos().add(0,3,0),90,true);});
                }
            }
            case "DEATH_KNIGHT_DEATH_STRIKE_ST" -> strike(player,state,positive(parameters,"damage",3));
            case "DEATH_KNIGHT_PHANTOM_CHARGE" -> dash(player,state,positive(parameters,"damage",4),15,1,true);
            case "ANTI_MAGE_KNIGHT_HEROIC_SURGE" -> dash(player,state,8,8,.82,false);
            case "ANTI_MAGE_KNIGHT_NORTHERN_GUST" -> wave(player,14,1,2.8,4,true);
            case "ANTI_MAGE_KNIGHT_CLAP" -> wave(player,12,1.15,3,7,false);
            case "DEATH_KNIGHT_WRAITHBOUND_CHAINS" -> chains(player,positive(parameters,"damage",3));
            case "DEATH_KNIGHT_SOUL_BARRIER" -> {state.barrierUntil=now+(long)(Math.min(60,positive(parameters,"duration",3))*20);ring(state.world,player.getPos().add(0,1,0),2,SOUL);}
            case "ANTI_MAGE_KNIGHT_OVERLOAD" -> {if(state.guardUntil>now)return false;state.guardUntil=now+120;state.stored=0;ring(state.world,player.getPos(),10,ParticleTypes.END_ROD);}
            case "DEATH_KNIGHT_NECROTIC_WHIRLWIND" -> whirlwind(player,positive(parameters,"damage",2.5),positive(parameters,"heal",2));
            default -> {return false;}
        }
        return true;
    }
    private static void strike(ServerPlayerEntity player,State state,double damage) {
        long now=SVFrameLibFabricMod.currentTick();if(now>=state.comboUntil)state.combo=0;
        int combo=state.combo;state.combo=(combo+1)%4;state.comboUntil=now+300;
        String animation=combo==1?"right-left-diag":combo==3?"up-down-vert":"left-right-diag";
        NativeVisualRuntime.spawn("vfx_death_strike_1@"+animation,player,player.getPos().add(forward(player).multiply(.2)).add(0,1.4,0),18,false);
        if(combo==2){grapple(player,damage*.5);return;}
        if(combo==3){velocity(player,forward(player).multiply(-.8));SVFrameLibFabricMod.schedule(7,()->{if(valid(player,state.world))wave(player,22,1,1.4,damage*1.5,false);});}
        for(LivingEntity target:targets(player,player.getPos().add(forward(player).multiply(.3)),4))
            if(target.getPos().subtract(player.getPos()).dotProduct(forward(player))>=-.5 && hit(player,target,damage*(combo==3?1.5:1)))velocity(target,away(player,target,.5,.1));
        ring(state.world,player.getPos().add(0,1.2,0),3,SOUL);
        new FabricScriptPlatform().sound(player.getUuid(),"death_knight_sounds:samus.death_knight.death_slash",.7f,.9f);
    }
    private static void grapple(ServerPlayerEntity player,double damage) {
        ServerWorld world=player.getServerWorld();Vec3d start=player.getEyePos(),end=start.add(player.getRotationVec(1).multiply(10));
        var block=world.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
        Vec3d contact=block.getType()==HitResult.Type.MISS?end:block.getPos();
        LivingEntity selected=null;double nearest=start.squaredDistanceTo(contact);
        for(LivingEntity target:world.getEntitiesByClass(LivingEntity.class,new Box(start,contact).expand(.8),t->canTarget(player,t))) {
            var intersection=target.getBoundingBox().expand(.8).raycast(start,contact);
            if(intersection.isPresent()&&start.squaredDistanceTo(intersection.get())<nearest){selected=target;contact=intersection.get();nearest=start.squaredDistanceTo(contact);}
        }
        line(world,start,contact,SOUL);
        if(selected!=null)hit(player,selected,damage);
        if(selected==null&&block.getType()==HitResult.Type.MISS)return;
        Vec3d anchor=contact;
        class Pull implements Runnable{int age;public void run(){
            if(!valid(player,world)||age++>=12||player.getEyePos().distanceTo(anchor)<2)return;
            Vec3d motion=anchor.subtract(player.getEyePos()).normalize().multiply(.8);
            velocity(player,motion);line(world,player.getEyePos(),anchor,SOUL);SVFrameLibFabricMod.schedule(1,this);
        }}new Pull().run();
    }
    public static double executionDamage(ServerPlayerEntity caster,LivingEntity target,double base) {
        Mark mark=MARKS.get(target);
        return mark!=null&&mark.owner.equals(caster.getUuid())&&mark.expires>SVFrameLibFabricMod.currentTick()&&mark.stacks==4?base*3.25:base;
    }
    private static void dash(ServerPlayerEntity player,State state,double damage,int duration,double speed,boolean death) {
        state.busyUntil=SVFrameLibFabricMod.currentTick()+duration;Vec3d direction=forward(player);Set<UUID> hits=new HashSet<>();
        class Dash implements Runnable {int age;public void run(){
            if(!valid(player,state.world)||age++>=duration)return;
            Vec3d to=player.getPos().add(direction.multiply(speed));
            if(state.world.raycast(new RaycastContext(player.getPos().add(0,.5,0),to.add(0,.5,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player)).getType()!=HitResult.Type.MISS)return;
            velocity(player,direction.multiply(speed).add(0,.12,0));
            particles(state.world,death?SOUL:ParticleTypes.CLOUD,player.getPos().add(0,1,0),6,.4);
            for(LivingEntity target:targets(player,player.getPos(),death?4.5:2.4)){
                boolean applied=hits.contains(target.getUuid());
                if(!applied){applied=hit(player,target,damage);if(applied)hits.add(target.getUuid());}
                if(applied)velocity(target,death?direction.multiply(.7).add(0,.1,0):direction.multiply(-.9).add(0,.45,0));
            }
            SVFrameLibFabricMod.schedule(1,this);
        }}new Dash().run();
    }
    private static void wave(ServerPlayerEntity player,double range,double speed,double width,double damage,boolean gust) {
        ServerWorld world=player.getServerWorld();Vec3d direction=player.getRotationVec(1).normalize(),start=player.getEyePos();Set<UUID> hits=new HashSet<>();
        class Wave implements Runnable {Vec3d point=start;double distance;public void run(){
            if(!valid(player,world)||distance>=range)return;Vec3d next=point.add(direction.multiply(Math.min(speed,range-distance)));
            var block=world.raycast(new RaycastContext(point,next,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
            if(block.getType()!=HitResult.Type.MISS||!world.isChunkLoaded(net.minecraft.util.math.BlockPos.ofFloored(next)))return;
            for(LivingEntity target:world.getEntitiesByClass(LivingEntity.class,new Box(point,next).expand(width),t->canTarget(player,t))) {
                Box swept=target.getBoundingBox().expand(width);
                if((swept.contains(point)||swept.raycast(point,next).isPresent())&&!hits.contains(target.getUuid())&&hit(player,target,damage)){
                    hits.add(target.getUuid());velocity(target,away(player,target,.65,.15));
                }
            }
            point=next;distance+=speed;
            particles(world,gust?ParticleTypes.CLOUD:SOUL,point,8,gust?.18:.45);
            if(gust){Vec3d side=new Vec3d(-direction.z,0,direction.x).normalize().multiply(Math.sin(distance*Math.PI/10)*2.2);particles(world,ParticleTypes.CLOUD,point.add(side),4,.18);particles(world,ParticleTypes.CLOUD,point.subtract(side),4,.18);}
            if(distance>=range&&gust){ring(world,point,4.5,ParticleTypes.CLOUD);for(LivingEntity target:targets(player,point,4.5))velocity(target,away(player,target,.35,.3));}
            SVFrameLibFabricMod.schedule(2,this);
        }}new Wave().run();
    }
    private static void chains(ServerPlayerEntity player,double damage) {
        ServerWorld world=player.getServerWorld();Vec3d dir=forward(player);
        for(LivingEntity target:targets(player,player.getPos().add(dir.multiply(4)),5)) {
            if(target.getPos().subtract(player.getPos()).normalize().dotProduct(dir)<.7 || !lineOfSight(player,target))continue;
            if(hit(player,target,damage)) {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,56,2));
                velocity(target,player.getPos().subtract(target.getPos()).normalize().multiply(.55).add(0,.12,0));
                line(world,player.getEyePos(),target.getPos().add(0,1,0),SOUL);
            }
        }
    }
    private static void whirlwind(ServerPlayerEntity player,double damage,double heal) {
        ServerWorld world=player.getServerWorld();
        class Whirl implements Runnable{int age;public void run(){
            if(!valid(player,world)||age>80)return;
            ring(world,player.getPos().add(0,1,0),5.5,SOUL);
            if(age%10==0)for(LivingEntity target:targets(player,player.getPos(),5.5))if(hit(player,target,age==80?damage*1.45:damage)) {
                velocity(target,age==80?away(player,target,.7,.15):new Vec3d(0,.25,0));
                if(age==80){target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,60,1));player.heal((float)heal);}
            }
            age+=5;SVFrameLibFabricMod.schedule(5,this);
        }}new Whirl().run();
    }
    /** Runs after RPG mitigation. Re-entrant damage emitted here cannot recursively proc the passive. */
    public static float modifyDamage(LivingEntity target,DamageSource source,float amount) {
        if(amount<=0||target.getWorld().isClient())return amount;
        long now=SVFrameLibFabricMod.currentTick();
        if(!OWN_DAMAGE.get() && source.isOf(DamageTypes.PLAYER_ATTACK) && source.getAttacker() instanceof ServerPlayerEntity attacker) {
            for(var entry:STATES.entrySet())if(entry.getValue().guardUntil>now&&entry.getKey()!=target && canTarget(entry.getKey(),attacker) && attacker.squaredDistanceTo(entry.getKey())<=100) {
                boolean applied=hit(attacker,entry.getKey(),amount);if(applied)return 0;
            }
            State attack=STATES.get(attacker);
            if(attack!=null&&attack.amkUntil>now && attack.sealReady<=now && canTarget(attacker,target)) {attack.sealReady=now+600;amount=amount*1.25f+2;particles(attacker.getServerWorld(),ParticleTypes.CRIT,target.getPos().add(0,1,0),30,.55);}
            if(attack!=null&&attack.curseUntil>now&&canTarget(attacker,target))addMark(attacker,target);
        }
        if(target instanceof ServerPlayerEntity player) {
            State state=STATES.get(player);
            if(state!=null) {
                if(state.barrierUntil>now){player.heal(amount);particles(state.world,SOUL,player.getPos().add(0,1,0),20,.5);return 0;}
                if(state.guardUntil>now){state.stored+=amount;amount*=.4f;}
                if(state.amkUntil>now){if(state.shield<=0&&state.shieldReady<=now){state.shield=player.getMaxHealth()*.16;state.shieldReady=now+600;}
                    double absorbed=Math.min(amount,state.shield);state.shield-=absorbed;amount-=absorbed;}
            }
        }
        return Math.max(0,amount);
    }
    public static boolean hit(ServerPlayerEntity player,LivingEntity target,double damage) {
        if(!canTarget(player,target)||damage<=0)return false;
        boolean prior=OWN_DAMAGE.get();OWN_DAMAGE.set(true);
        try {
            var attacker=new PlayerMetadata(MMOPlayerData.setup(player).getStatMap(),EquipmentSlot.MAIN_HAND);
            boolean applied=SVFrameLib.inst().getDamage().registerAttack(new AttackMetadata(new DamageMetadata(Math.min(10000,damage),List.of(DamageType.MAGIC,DamageType.SKILL)),target,attacker),false);
            State state=STATES.get(player);if(applied && state!=null&&state.curseUntil>SVFrameLibFabricMod.currentTick())addMark(player,target);
            return applied;
        } finally {OWN_DAMAGE.set(prior);}
    }
    private static void addMark(ServerPlayerEntity caster,LivingEntity target) {
        long now=SVFrameLibFabricMod.currentTick();Mark previous=MARKS.get(target);
        int stacks=previous!=null&&previous.owner.equals(caster.getUuid())&&previous.expires>now?Math.min(4,previous.stacks+1):1;
        MARKS.put(target,new Mark(caster.getUuid(),stacks,now+200));
        if(stacks==4)target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,100,0));
        ring(caster.getServerWorld(),target.getPos().add(0,target.getHeight()+.3,0),.25+stacks*.08,SOUL);
    }
    public static boolean canTarget(ServerPlayerEntity caster,LivingEntity target) {
        return target!=caster && target.isAlive()&&!target.isSpectator()&&!(target instanceof ArmorStandEntity)&&target.getWorld()==caster.getWorld()
                &&(!(target instanceof ServerPlayerEntity other)||(caster.getServerWorld().getServer().isPvpEnabled()&&!other.getAbilities().creativeMode&&!caster.isTeammate(other)));
    }
    private static List<LivingEntity> targets(ServerPlayerEntity player,Vec3d center,double radius) {
        return player.getServerWorld().getEntitiesByClass(LivingEntity.class,new Box(center,center).expand(radius),t->canTarget(player,t)&&t.squaredDistanceTo(center)<=radius*radius).stream()
                .sorted(Comparator.comparingDouble(t->t.squaredDistanceTo(center))).limit(32).toList();
    }
    private static boolean valid(ServerPlayerEntity player,ServerWorld world) {return player.isAlive()&&!player.isRemoved()&&player.getWorld()==world&&player.networkHandler!=null;}
    private static boolean lineOfSight(ServerPlayerEntity player,LivingEntity target) {return player.getServerWorld().raycast(new RaycastContext(player.getEyePos(),target.getEyePos(),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player)).getType()==HitResult.Type.MISS;}
    private static Vec3d forward(ServerPlayerEntity player) {Vec3d d=player.getRotationVec(1);return new Vec3d(d.x,0,d.z).normalize();}
    private static Vec3d away(Entity caster,Entity target,double horizontal,double vertical) {Vec3d delta=target.getPos().subtract(caster.getPos());return new Vec3d(delta.x,0,delta.z).normalize().multiply(horizontal).add(0,vertical,0);}
    private static void velocity(LivingEntity entity,Vec3d motion) {entity.setVelocity(motion);entity.velocityModified=true;entity.velocityDirty=true;if(entity instanceof ServerPlayerEntity player)player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));}
    private static void particles(ServerWorld world,ParticleEffect effect,Vec3d point,int count,double spread) {world.spawnParticles(effect,point.x,point.y,point.z,count,spread,spread,spread,0);}
    private static void ring(ServerWorld world,Vec3d point,double radius,ParticleEffect particle) {for(int i=0;i<48;i++){double angle=i*Math.PI*2/48;particles(world,particle,point.add(Math.cos(angle)*radius,.12,Math.sin(angle)*radius),1,0);}}
    private static void line(ServerWorld world,Vec3d from,Vec3d to,ParticleEffect particle) {for(int i=0;i<24;i++)particles(world,particle,from.lerp(to,i/23d),1,0);}
    private static double positive(Map<String,?> parameters,String key,double fallback){Object value=parameters.get(key);return value instanceof Number n&&Double.isFinite(n.doubleValue())&&n.doubleValue()>0?Math.min(10000,n.doubleValue()):fallback;}
    public static int markStacks(LivingEntity target){Mark mark=MARKS.get(target);return mark==null?0:mark.stacks;}
    private record Mark(UUID owner,int stacks,long expires) { }
    private static final class State {
        final ServerWorld world;long curseUntil,amkUntil,sealReady,shieldReady,barrierUntil,guardUntil,busyUntil,comboUntil;int combo;double shield,stored;
        State(ServerWorld world){this.world=world;}
    }
}
