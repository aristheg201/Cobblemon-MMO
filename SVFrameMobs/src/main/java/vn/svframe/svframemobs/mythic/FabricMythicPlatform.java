package vn.svframe.svframemobs.mythic;

import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.slf4j.LoggerFactory;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.provider.EntityStatProvider;
import vn.svframe.svframelib.damage.*;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframelib.fabric.NativeParticles;
import vn.svframe.svframelib.fabric.runtime.script.ExpressionRuntime;
import java.util.*;
import java.util.regex.*;
import static vn.svframe.svframemobs.mythic.MythicEngine.*;

/** Native world effects; gameplay and hit tests never depend on the client. */
public final class FabricMythicPlatform implements MythicEngine.Platform {
    private static final Pattern PLACEHOLDER=Pattern.compile("<([^<>]+)>");
    private static final Set<String> COMMON=Set.of("repeat","repeatinterval","delay");
    private static final Map<String,Set<String>> ACTIONS=new LinkedHashMap<>();
    private static final Set<String> SELECTORS=Set.of("self","caster","selflocation","selfeyelocation","target","t","trigger","origin","targetlocation","forward","pir","playersinradius","playersnearorigin","eir","entitiesinradius","livingentitiesinradius","livinginradius","entitiesnearorigin","eno","mir","mobsinradius","mobsnearorigin","nearestplayer");
    private static final Set<String> CONDITIONS=Set.of("isliving","isplayer","iscaster","crouching","iscrouching","issprinting","blocking","isblocking","onground","hastag","entitytype","mythicmobtype","targetwithin","targetnotwithin","distance","health","chance","hasaura","hasaurastacks","offgcd","incombat","lineofsight","targetinlineofsight","holding","stance","varequals","variableinrange","stringequals","mmocantarget");
    static {
        fields("skill","s skill");fields("delay","ticks t");fields("randomskill","skills s");
        fields("damage mmodamage","a amount pkb preventknockback hnp hp type ignoreinvulnerability preventimmunity pi damagecause dc");
        fields("heal","a amount percent");fields("potion","type duration level hasparticles ambient overwrite icon");
        fields("velocity","x y z mode");fields("throw","v velocity vy velocityy");fields("lunge","v velocity vy velocityy");
        fields("pull","v velocity toorigin to");
        fields("teleport","");fields("addtag removetag","t tag tags");fields("remove","delay");fields("ignite","ticks duration");fields("extinguish"," ");
        fields("setai ai","ai");fields("setspeed","speed s type");fields("gcd","ticks");fields("setstance","stance s");
        fields("message actionmessage","m message");fields("sound effect:sound","s sound v volume p pitch");
        fields("particle particles effect:particles","p particle a amount count hs vs hspread vspread y speed color c size material forwardoffset sideoffset");
        fields("effect:particlering effect:particlesphere","p particle a amount count r radius points y speed color c size material hs vs hspread vspread");
        fields("projectile","ontick onhit onend onstart v velocity i interval hr vr mr maxrange hnp hp sb se syo tyo g gravity ho hs hfs");
        fields("totem","ontick onend onstart md maxduration duration d i interval yoffset yo hnp hp");
        fields("stun","duration d f face facing cancelontakedamage cancelondeath");
        fields("ondamaged","a auraname an duration d ce cancelevent damagemods");
        fields("hidefromplayers","d duration");fields("lightning","d damage");
        fields("aura","a aur an auraname duration d interval i ontick onend onstart maxstacks stacks refreshduration");fields("auraremove","a aur an auraname");
        fields("setvariable variableadd variablesubtract","var variable value v type");
    }
    private static void fields(String names,String fields){Set<String> allowed=new HashSet<>(COMMON);for(String key:fields.split(" "))if(!key.isBlank())allowed.add(key);for(String name:names.split(" "))ACTIONS.put(name,Set.copyOf(allowed));}
    private record AuraKey(UUID entity,String name) { }
    private record Aura(int stacks,long until,Context context,MythicLine action) { }
    private final ExpressionRuntime expressions=new ExpressionRuntime();
    private final Map<AuraKey,Aura> auras=new HashMap<>();
    private final Map<UUID,Map<String,String>> variables=new HashMap<>();
    private final Map<UUID,Long> globalCooldown=new HashMap<>(),combat=new HashMap<>();
    private record DamageWard(long until, boolean all, Set<String> causes) {}
    private final Map<AuraKey,DamageWard> damageWards=new HashMap<>();
    private record Stun(UUID actor,Point anchor,float yaw,float pitch,long until,boolean facing,boolean cancelDamage,boolean cancelDeath) {}
    private final Map<UUID,Stun> stuns=new HashMap<>();
    private void stun(MythicLine line,Context context,Target target) {
        Entity actor=entity(context.caster()),victim=entity(target.entity());
        if(!(victim instanceof LivingEntity living)||!RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.STATUS)||!RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.MOVEMENT))return;
        int duration=MythicEngine.ticks(n(line,"duration","d",100,context,target),1,72000);
        stuns.put(victim.getUuid(),new Stun(context.caster(),point(victim),victim.getYaw(),victim.getPitch(),tick+duration,
            bool(line,"facing",bool(line,"face",bool(line,"f",false))),bool(line,"cancelontakedamage",false),bool(line,"cancelondeath",true)));
    }
    public void damaged(LivingEntity victim) {Stun stun=stuns.get(victim.getUuid());if(stun!=null&&stun.cancelDamage())stuns.remove(victim.getUuid());}
    private void tickStuns() {
        var iterator=stuns.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();Stun stun=entry.getValue();Entity victim=entity(entry.getKey());
            if(victim==null||victim.isRemoved()||tick>=stun.until()||(!victim.isAlive()&&stun.cancelDeath())||!point(victim).world().equals(stun.anchor().world())){iterator.remove();continue;}
            if(victim instanceof LivingEntity living&&living.isAlive()&&!RpgEntityAdapters.allows(entity(stun.actor()),living,RpgEntityAdapters.Effect.MOVEMENT)){iterator.remove();continue;}
            float yaw=stun.facing()?stun.yaw():victim.getYaw(),pitch=stun.facing()?stun.pitch():victim.getPitch();
            if(victim.squaredDistanceTo(vector(stun.anchor()))>.000001||(stun.facing()&&(victim.getYaw()!=yaw||victim.getPitch()!=pitch))) {
                if(victim instanceof ServerPlayerEntity player)player.networkHandler.requestTeleport(stun.anchor().x(),stun.anchor().y(),stun.anchor().z(),yaw,pitch);
                else victim.refreshPositionAndAngles(stun.anchor().x(),stun.anchor().y(),stun.anchor().z(),yaw,pitch);
            }
            victim.setVelocity(Vec3d.ZERO);victim.velocityDirty=true;
        }
    }
    private MinecraftServer server;private long tick;private int particleBudget;
    public void server(MinecraftServer server){this.server=server;}
    public void tick(long tick){this.tick=tick;particleBudget=2048;tickStuns();NativeVisibility.tick(server,tick);damageWards.entrySet().removeIf(r->r.getValue().until()<=tick);if(tick%200==0){globalCooldown.entrySet().removeIf(r->r.getValue()<=tick);combat.entrySet().removeIf(r->r.getValue()<=tick);variables.keySet().removeIf(id->entity(id)==null);auras.entrySet().removeIf(row->row.getValue().until()<=tick||entity(row.getKey().entity())==null);}}
    public void clear(){stuns.clear();damageWards.clear();NativeVisibility.clear();auras.clear();variables.clear();globalCooldown.clear();combat.clear();server=null;}
    public Entity entity(UUID id) {if(id==null||server==null)return null;for(ServerWorld world:server.getWorlds()){Entity found=world.getEntity(id);if(found!=null)return found;}return null;}
    public static Point point(Entity entity){return new Point(entity.getWorld().getRegistryKey().getValue().toString(),entity.getX(),entity.getY(),entity.getZ());}
    private ServerWorld world(Point point){if(server==null||point==null)return null;for(ServerWorld world:server.getWorlds())if(world.getRegistryKey().getValue().toString().equals(point.world()))return world;return null;}
    private Point position(Target target,Context context){Entity entity=target==null?null:entity(target.entity());return target!=null&&target.point()!=null?target.point():entity==null?context.origin():point(entity);}
    private static Vec3d vector(Point p){return new Vec3d(p.x(),p.y(),p.z());}
    @Override public boolean valid(Context context){Entity actor=entity(context.caster());return actor!=null&&!actor.isRemoved()&&context.origin()!=null&&point(actor).world().equals(context.origin().world())&&(actor.isAlive()||context.parameters().containsKey("__death_trigger"));}
    @Override public void failed(String skill,Exception error){LoggerFactory.getLogger("SVFrameMobs").warn("Mythic execution rejected for {}: {}",skill,error.toString());}
    @Override public boolean externalAvailable(String id){return vn.svframe.svframelib.fabric.SVFrameLibFabricMod.hasSkill(id);}
    public void markCombat(Entity entity){if(entity!=null)combat.put(entity.getUuid(),tick+200);}
    @Override public String text(String input,Context context,Target target) {
        Matcher matcher=PLACEHOLDER.matcher(input);StringBuilder result=new StringBuilder();
        while(matcher.find()) {
            String key=matcher.group(1),value=null;
            if(key.startsWith("modifier."))value=context.parameters().get(key.substring(9));
            else if(key.startsWith("skill.var."))value=context.variables().getOrDefault(key.substring(10),context.parameters().get(key.substring(10)));
            else if(key.startsWith("caster.var."))value=variables.getOrDefault(context.caster(),Map.of()).get(key.substring(11));
            else if(key.startsWith("target.var.")&&target!=null)value=variables.getOrDefault(target.entity(),Map.of()).get(key.substring(11));
            else if(key.startsWith("random.")) {
                String[] parts=key.substring(7).split("to",2);if(parts.length==2){double min=Double.parseDouble(parts[0]),max=Double.parseDouble(parts[1]);value=Double.toString(min+Math.random()*(max-min));}
            }else {
                Entity e=key.startsWith("caster.")?entity(context.caster()):key.startsWith("target.")&&target!=null?entity(target.entity()):null;
                if(e!=null)value=switch(key.substring(key.indexOf('.')+1)){
                    case "uuid"->e.getUuidAsString();case "name"->e.getName().getString();case "health"->e instanceof LivingEntity l?Float.toString(l.getHealth()):"0";
                    case "mhp","maxhealth"->e instanceof LivingEntity l?Float.toString(l.getMaxHealth()):"0";
                    case "location.x"->Double.toString(e.getX());case "location.y"->Double.toString(e.getY());case "location.z"->Double.toString(e.getZ());default->null;
                };
            }
            if(value==null)throw new IllegalArgumentException("Unresolved Mythic placeholder <"+key+">");
            matcher.appendReplacement(result,Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);return result.toString();
    }
    @Override public double number(String expression,Context context,Target target){
        String source=text(expression,context,target);double value=expressions.evaluate(source,Map.of());
        if(!Double.isFinite(value)||Math.abs(value)>1_000_000)throw new IllegalArgumentException("Invalid skill number "+expression);return value;
    }
    private double n(MythicLine line,String key,double fallback,Context c,Target t){return number(line.value(key,Double.toString(fallback)),c,t);}
    private double n(MythicLine line,String key,String alias,double fallback,Context c,Target t){return number(line.value(key,line.value(alias,Double.toString(fallback))),c,t);}
    private static boolean bool(MythicLine line,String key,boolean fallback){String value=line.value(key,Boolean.toString(fallback));if(!Set.of("true","false").contains(value.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Expected boolean "+key);return Boolean.parseBoolean(value);}
    private static Identifier id(String input){return Identifier.of(input.contains(":")?input.toLowerCase(Locale.ROOT):"minecraft:"+input.toLowerCase(Locale.ROOT));}
    private static Identifier potionId(String raw){return id(vn.svframe.svframemobs.PotionParameters.type(raw));}
    private int potionInteger(MythicLine line,String key,int fallback,int min,int max,Context context,Target target){
        double value=expressions.evaluate(text(line.value(key,Integer.toString(fallback)),context,target),Map.of());
        return vn.svframe.svframemobs.PotionParameters.integer(value,min,max);
    }
    @Override public List<Target> select(MythicLine.Selector selector,Context context) {
        Entity caster=entity(context.caster());if(caster==null)return List.of();String name=selector.name();Map<String,String> parameters=selector.parameters();
        if(Set.of("self","caster").contains(name))return List.of(new Target(caster.getUuid(),null));
        if(name.equals("selflocation")||name.equals("selfeyelocation")){
            double offset=number(parameters.getOrDefault("y","0"),context,context.target());
            if(Math.abs(offset)>64)throw new IllegalArgumentException("Location offset exceeds range");
            return List.of(new Target(null,point(caster).add(0,offset+(name.equals("selfeyelocation")?caster.getStandingEyeHeight():0),0)));
        }
        if(name.equals("trigger"))return entity(context.trigger())==null?List.of():List.of(new Target(context.trigger(),null));
        if(Set.of("target","t","targetlocation").contains(name)) {
            Entity target=context.target()!=null&&!Objects.equals(context.target().entity(),caster.getUuid())?entity(context.target().entity()):caster instanceof MobEntity mob?mob.getTarget():null;
            return target==null?List.of():List.of(name.equals("targetlocation")?new Target(null,point(target)):new Target(target.getUuid(),null));
        }
        if(name.equals("origin"))return List.of(new Target(null,context.origin()));
        if(name.equals("forward")) {
            double sideOffset=number(parameters.getOrDefault("sideoffset","0"),context,context.target());
            double forward=number(parameters.getOrDefault("f","5"),context,context.target()),y=number(parameters.getOrDefault("y","0"),context,context.target());
            if(Math.abs(forward)>64||Math.abs(y)>64)throw new IllegalArgumentException("Forward target exceeds range");
            Vec3d direction=caster.getRotationVec(1);if(Boolean.parseBoolean(parameters.getOrDefault("lockpitch","false")))direction=new Vec3d(direction.x,0,direction.z).normalize();
            return List.of(new Target(null,point(caster).add(direction.x*forward-direction.z*sideOffset,direction.y*forward+y,direction.z*forward+direction.x*sideOffset)));
        }
        Point center=name.contains("origin")||name.equals("eno")?context.origin():point(caster);ServerWorld world=world(center);if(world==null)return List.of();
        double radius=number(parameters.getOrDefault("r",name.equals("nearestplayer")?"32":"5"),context,context.target());
        boolean players=name.equals("pir")||name.startsWith("players")||name.equals("nearestplayer"),mobs=name.equals("mir")||name.startsWith("mobs");
        if(radius<0||radius>(players?160:64))throw new IllegalArgumentException("Invalid target radius");
        Vec3d origin=vector(center);List<LivingEntity> candidates=world.getEntitiesByClass(LivingEntity.class,Box.of(origin,radius*2,radius*2,radius*2),e->e.isAlive()&&!e.isSpectator()&&e!=caster&&e.squaredDistanceTo(origin)<=radius*radius&&(!players||e instanceof ServerPlayerEntity)&&(!mobs||e instanceof MobEntity));
        String sort=parameters.getOrDefault("sort","NONE").toUpperCase(Locale.ROOT);
        if(sort.equals("NEAREST")||name.equals("nearestplayer"))candidates.sort(Comparator.comparingDouble(e->e.squaredDistanceTo(origin)));
        else if(sort.equals("FURTHEST"))candidates.sort(Comparator.comparingDouble((LivingEntity e)->e.squaredDistanceTo(origin)).reversed());
        else if(sort.equals("RANDOM"))Collections.shuffle(candidates);
        // The engine applies inline conditions before the target limit. An
        // excluded armor stand must not consume a real enemy's target slot.
        return candidates.stream().map(e->new Target(e.getUuid(),null)).toList();
    }
    @Override public boolean condition(MythicLine.Condition test,Context context,Target selected) {
        Entity e=selected==null?null:entity(selected.entity()),caster=entity(context.caster());Map<String,String> p=test.parameters();
        return switch(test.name()) {
            case "isliving"->e instanceof LivingEntity;case "isplayer"->e instanceof ServerPlayerEntity;case "iscaster"->e!=null&&e==caster;
            case "crouching","iscrouching"->e!=null&&e.isSneaking();case "issprinting"->e!=null&&e.isSprinting();case "onground"->e!=null&&e.isOnGround();
            case "blocking","isblocking"->e instanceof LivingEntity living&&living.isBlocking();
            case "hastag"->e!=null&&e.getCommandTags().contains(p.getOrDefault("t",p.getOrDefault("tag","")));
            case "entitytype"->e!=null&&Arrays.stream(p.getOrDefault("t",p.getOrDefault("type","")).split(",")).anyMatch(type->Registries.ENTITY_TYPE.getId(e.getType()).equals(id(type.trim())));
            case "mythicmobtype"->e!=null&&Arrays.stream(p.getOrDefault("t",p.getOrDefault("type","")).split(",")).anyMatch(type->e.getCommandTags().contains("svframemobs:"+type.trim()));
            case "targetwithin","targetnotwithin"->{Entity target=entity(context.target().entity());double radius=number(p.getOrDefault("d",p.getOrDefault("distance","5")),context,selected);boolean within=e!=null&&target!=null&&e.squaredDistanceTo(target)<=radius*radius;yield test.name().equals("targetwithin")?within:!within;}
            case "distance"->{Entity target=entity(context.target().entity());yield e!=null&&target!=null&&range(p.getOrDefault("d",p.getOrDefault("distance","0-5")),e.distanceTo(target),context,selected);}
            case "health"->e instanceof LivingEntity living&&range(p.getOrDefault("h",p.getOrDefault("health","0-100%")),living.getHealth(),context,selected,living.getMaxHealth());
            case "chance"->Math.random()<number(p.getOrDefault("chance",p.getOrDefault("c","1")),context,selected);
            case "hasaura","hasaurastacks"->{Aura aura=e==null?null:auras.get(new AuraKey(e.getUuid(),auraName(p)));yield aura!=null&&aura.until()>tick&&(!test.name().equals("hasaurastacks")||range(p.getOrDefault("stacks",p.getOrDefault("s",">0")),aura.stacks(),context,selected));}
            case "offgcd"->e!=null&&globalCooldown.getOrDefault(e.getUuid(),0L)<=tick;
            case "incombat"->e!=null&&(combat.getOrDefault(e.getUuid(),0L)>tick||e instanceof MobEntity mob&&mob.getTarget()!=null);
            case "lineofsight","targetinlineofsight"->{Entity target=entity(context.target().entity());yield e instanceof LivingEntity living&&target!=null&&living.canSee(target);}
            case "holding"->e instanceof LivingEntity living&&Registries.ITEM.getId(living.getMainHandStack().getItem()).equals(id(p.getOrDefault("m",p.getOrDefault("material","air"))));
            case "stance"->e!=null&&variables.getOrDefault(e.getUuid(),Map.of()).getOrDefault("stance","").equals(p.getOrDefault("s",p.getOrDefault("stance","")));
            case "stringequals"->text(p.getOrDefault("val1",""),context,selected).equals(text(p.getOrDefault("val2",""),context,selected));
            case "varequals"->{String left=text("<"+p.getOrDefault("var","skill.var.value")+">",context,selected),right=text(p.getOrDefault("value",""),context,selected);boolean equal;try{equal=Double.parseDouble(left)==Double.parseDouble(right);}catch(NumberFormatException ignored){equal=left.equals(right);}yield equal;}
            case "variableinrange"->range(p.getOrDefault("value",p.getOrDefault("range",">0")),number("<"+p.getOrDefault("var","skill.var.value")+">",context,selected),context,selected);
            case "mmocantarget"->e instanceof LivingEntity living&&caster!=null&&e!=caster&&!caster.isTeammate(e)&&RpgEntityAdapters.allows(caster,living,RpgEntityAdapters.Effect.DAMAGE);
            default->throw new IllegalArgumentException("Unsupported condition "+test.name());
        };
    }
    private boolean range(String expression,double value,Context c,Target t){return range(expression,value,c,t,100);}
    private boolean range(String expression,double value,Context c,Target t,double maximum) {
        String input=text(expression,c,t).trim();boolean percent=input.endsWith("%");if(percent){input=input.substring(0,input.length()-1);value=value/maximum*100;}
        for(String op:List.of(">=","<=",">","<","="))if(input.startsWith(op)){double test=number(input.substring(op.length()),c,t);return switch(op){case ">="->value>=test;case "<="->value<=test;case ">"->value>test;case "<"->value<test;default->value==test;};}
        Matcher pair=Pattern.compile("(-?\\d+(?:\\.\\d+)?)-(-?\\d+(?:\\.\\d+)?)").matcher(input);if(pair.matches())return value>=Double.parseDouble(pair.group(1))&&value<=Double.parseDouble(pair.group(2));return value==number(input,c,t);
    }
    @Override public void execute(MythicLine line,Context context,Target target,MythicEngine engine) {
        Entity actor=entity(context.caster()),victim=entity(target.entity());Point location=position(target,context);ServerWorld world=world(location);if(actor==null||world==null)return;
        switch(line.mechanic()) {
            case "skill"->vn.svframe.svframelib.fabric.SVFrameLibFabricMod.castSkill(line.value("s",line.value("skill","")),context.caster(),target.entity(),context.parameters());
            case "damage","mmodamage"->{if(!(victim instanceof LivingEntity living)||!(actor instanceof LivingEntity source)||(actor==victim&&!explicitSelf(line))||(actor!=victim&&actor.isTeammate(victim)))return;
                if(!bool(line,victim instanceof ServerPlayerEntity?"hp":"hnp",true)||!RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.DAMAGE))return;
                double amount=n(line,"amount","a",1,context,target);if(amount<=0)return;
                vn.svframe.svframelib.api.stat.provider.StatProvider provider=source instanceof ServerPlayerEntity player?new vn.svframe.svframelib.player.PlayerMetadata(MMOPlayerData.setup(player).getStatMap(),vn.svframe.svframelib.api.player.EquipmentSlot.MAIN_HAND):new EntityStatProvider(source);
                DamageType type=line.value("type","SKILL").equalsIgnoreCase("MAGIC")?DamageType.MAGIC:line.value("type","SKILL").equalsIgnoreCase("PHYSICAL")?DamageType.PHYSICAL:DamageType.SKILL;
                SVFrameLib.plugin.getDamage().registerAttack(new AttackMetadata(new DamageMetadata(amount,List.of(DamageType.SKILL,type)),living,provider),!bool(line,"pkb",bool(line,"preventknockback",false)),bool(line,"ignoreinvulnerability",bool(line,"preventimmunity",bool(line,"pi",false))),damageSource(line,source,living));markCombat(actor);markCombat(victim);
            }
            case "heal"->{if(victim instanceof LivingEntity living){double amount=n(line,"amount","a",1,context,target);if(bool(line,"percent",false))amount*=living.getMaxHealth();RpgEntityAdapters.heal(actor,living,amount);}}
            case "potion"->{if(victim instanceof LivingEntity living&&RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.STATUS)) {
                var entry=Registries.STATUS_EFFECT.getEntry(potionId(line.value("type","slowness"))).orElseThrow();
                int duration=potionInteger(line,"duration",60,1,Integer.MAX_VALUE,context,target),level=potionInteger(line,"level",0,0,255,context,target);
                boolean particles=bool(line,"hasparticles",true);
                if(bool(line,"overwrite",false))living.removeStatusEffect(entry);
                living.addStatusEffect(new StatusEffectInstance(entry,duration,level,bool(line,"ambient",false),particles,bool(line,"icon",particles)),actor);
            }}
            case "velocity"->{if(victim!=null)move(actor,victim,new Vec3d(n(line,"x",0,context,target),n(line,"y",0,context,target),n(line,"z",0,context,target)),line.value("mode","SET"));}
            case "pull"->{if(victim!=null){
                Point destination=bool(line,"toorigin",bool(line,"to",false))?context.origin():point(actor);
                var motion=PullMotion.towards(point(victim),destination,n(line,"velocity","v",1,context,target));
                move(actor,victim,new Vec3d(motion.x(),motion.y(),motion.z()),"SET");
            }}
            case "throw"->{if(victim!=null){Vec3d direction=victim.getPos().subtract(actor.getPos()).multiply(1,0,1).normalize().multiply(n(line,"velocity","v",1,context,target)/10);move(actor,victim,direction.add(0,n(line,"velocityy","vy",1,context,target)/10,0),"SET");}}
            case "lunge"->{Vec3d direction=vector(location).subtract(actor.getPos()).multiply(1,0,1).normalize().multiply(n(line,"velocity","v",1,context,target));move(actor,actor,direction.add(0,n(line,"velocityy","vy",0,context,target),0),"SET");}
            case "teleport"->{if(world!=actor.getWorld()||!world.isChunkLoaded(BlockPos.ofFloored(vector(location)))||!world.getWorldBorder().contains(location.x(),location.z()))return;
                Box moved=actor.getBoundingBox().offset(vector(location).subtract(actor.getPos()));if(!world.isSpaceEmpty(actor,moved))return;
                if(actor instanceof ServerPlayerEntity player)player.networkHandler.requestTeleport(location.x(),location.y(),location.z(),player.getYaw(),player.getPitch());else actor.refreshPositionAfterTeleport(location.x(),location.y(),location.z());}
            case "addtag","removetag"->{if(victim!=null)for(String tag:text(line.value("tags",line.value("t",line.value("tag",""))),context,target).split(",")){if(tag.isBlank()||tag.length()>128)throw new IllegalArgumentException("Invalid tag");if(line.mechanic().equals("addtag"))victim.addCommandTag(tag.trim());else victim.removeCommandTag(tag.trim());}}
            case "remove"->{if(victim!=null&&!(victim instanceof ServerPlayerEntity)&&(!(victim instanceof LivingEntity living)||RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.DAMAGE)))victim.discard();}
            case "ignite"->{if(victim instanceof LivingEntity living&&RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.STATUS))victim.setOnFireForTicks(MythicEngine.ticks(n(line,"ticks","duration",60,context,target),1,72000));}
            case "extinguish"->{if(victim instanceof LivingEntity living&&RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.STATUS))victim.extinguish();}
            case "ai","setai"->{if(victim instanceof MobEntity mob&&RpgEntityAdapters.allows(actor,mob,RpgEntityAdapters.Effect.STATUS))mob.setAiDisabled(!bool(line,"ai",true));}
            case "setspeed"->{if(victim instanceof LivingEntity living&&RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.STATUS)){if(!line.value("type","WALKING").equalsIgnoreCase("WALKING"))throw new IllegalArgumentException("Unsupported movement type");var attribute=living.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attribute!=null){double speed=n(line,"speed","s",.2,context,target);if(speed<0||speed>3)throw new IllegalArgumentException("Invalid movement speed");Identifier key=Identifier.of("svframemobs","skill_speed");attribute.removeModifier(key);attribute.addTemporaryModifier(new EntityAttributeModifier(key,speed-attribute.getValue(),EntityAttributeModifier.Operation.ADD_VALUE));}}}
            case "gcd"->globalCooldown.put(actor.getUuid(),tick+MythicEngine.ticks(n(line,"ticks",20,context,target),1,72000));
            case "setstance"->{if(victim!=null)variables.computeIfAbsent(victim.getUuid(),k->new HashMap<>()).put("stance",line.value("stance",line.value("s","")));}
            case "message","actionmessage"->{if(victim instanceof ServerPlayerEntity player)player.sendMessage(Text.literal(text(line.value("m",line.value("message","")),context,target).replace('&','§')),line.mechanic().equals("actionmessage"));}
            case "effect:sound","sound"->world.playSound(null,location.x(),location.y(),location.z(),SoundEvent.of(id(line.value("s",line.value("sound","entity.player.levelup")))),SoundCategory.PLAYERS,(float)MathHelper.clamp(n(line,"volume","v",1,context,target),0,4),(float)MathHelper.clamp(n(line,"pitch","p",1,context,target),.01,2));
            case "particles","particle","effect:particles","effect:particlering","effect:particlesphere"->particles(line,context,target,world,location);
            case "totem"->totem(line,context,target,engine);
            case "stun"->stun(line,context,target);
            case "projectile"->projectile(line,context,target,engine);
            case "ondamaged"->damageWard(line,context,target);
            case "hidefromplayers"->{if(victim instanceof ServerPlayerEntity observer&&actor instanceof ServerPlayerEntity hidden&&observer!=hidden)
                NativeVisibility.hide(hidden,observer,tick+MythicEngine.ticks(n(line,"duration","d",100,context,target),1,72000));}
            case "lightning"->{LightningEntity bolt=EntityType.LIGHTNING_BOLT.create(world);if(bolt!=null){bolt.refreshPositionAfterTeleport(location.x(),location.y(),location.z());bolt.setCosmetic(true);world.spawnEntity(bolt);}}
            case "aura"->aura(line,context,target,engine);
            case "auraremove"->{if(victim!=null){AuraKey key=new AuraKey(victim.getUuid(),auraName(line.parameters()));Aura removed=auras.remove(key);if(removed!=null)callback(removed.action().value("onend",""),removed.context(),target,location,engine);}}
            case "setvariable","variableadd","variablesubtract"->variable(line,context,target);
            case "randomskill"->{String[] options=line.value("skills",line.value("s","")).split(",");double total=0;List<String> names=new ArrayList<>();List<Double> weights=new ArrayList<>();for(String item:options){String[] pair=item.trim().split("\\s+");double weight=pair.length==2?number(pair[1],context,target):1;if(weight<=0||pair.length>2)throw new IllegalArgumentException("Invalid random skill weight");names.add(pair[0]);weights.add(weight);total+=weight;}double pick=Math.random()*total;for(int i=0;i<names.size();i++){pick-=weights.get(i);if(pick<=0){engine.call(names.get(i),context.child(target,Map.of()));break;}}}
            default->throw new IllegalArgumentException("Unsupported native effect "+line.mechanic());
        }
    }
    private static boolean explicitSelf(MythicLine line) {
        return line.selector()!=null&&Set.of("self","caster").contains(line.selector().name());
    }
    private void totem(MythicLine line,Context context,Target target,MythicEngine engine) {
        Point anchor=position(target,context).add(0,n(line,"yoffset","yo",1,context,target),0);
        Target location=new Target(null,anchor);Context anchored=context.at(anchor).target(location);
        int duration=MythicEngine.ticks(n(line,"maxduration","md",n(line,"duration","d",400,context,target),context,target),1,72000);
        int interval=MythicEngine.ticks(n(line,"interval","i",1,context,target),1,72000);
        Entity caster=entity(context.caster());
        // Original player-only LOS check occurs once when the totem starts,
        // not repeatedly as the caster walks away from its fixed anchor.
        if(caster instanceof ServerPlayerEntity) {
            ServerWorld world=world(anchor);
            HitResult hit=world.raycast(new RaycastContext(caster.getPos().add(0,1,0),vector(anchor),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,caster));
            if(hit.getType()!=HitResult.Type.MISS) {
                Point impact=new Point(anchor.world(),hit.getPos().x,hit.getPos().y,hit.getPos().z);
                callback(line.value("onstart",""),anchored,location,anchor,engine);
                callback(line.value("onend",""),anchored,new Target(null,impact),impact,engine);return;
            }
        }
        AnchoredTotem.start(engine,anchored,duration,interval,()->{
            ServerWorld world=world(anchor);
            return world!=null&&world.isChunkLoaded(BlockPos.ofFloored(vector(anchor)));
        },()->callback(line.value("onstart",""),anchored,location,anchor,engine),
          ()->callback(line.value("ontick",""),anchored,location,anchor,engine),
          ()->callback(line.value("onend",""),anchored,location,anchor,engine));
    }
    private net.minecraft.entity.damage.DamageSource damageSource(MythicLine line,LivingEntity actor,LivingEntity target) {
        String cause=line.value("damagecause",line.value("dc",""));if(cause.isBlank())return null;
        return switch(cause.toLowerCase(Locale.ROOT)) {
            case "sonic_boom"->target.getDamageSources().sonicBoom(actor);
            case "magic"->target.getDamageSources().indirectMagic(actor,actor);
            case "lightning","lightning_bolt"->target.getDamageSources().lightningBolt();
            case "custom","generic"->target.getDamageSources().generic();
            default->throw new IllegalArgumentException("Unsupported damage cause "+cause);
        };
    }
    public boolean blocksDamage(LivingEntity entity,net.minecraft.entity.damage.DamageSource source) {
        String cause=source.getName().toUpperCase(Locale.ROOT);
        for(var entry:damageWards.entrySet())if(entry.getKey().entity().equals(entity.getUuid())&&entry.getValue().until()>tick
                &&(entry.getValue().all()||entry.getValue().causes().contains(cause)))return true;
        return false;
    }
    private void damageWard(MythicLine line,Context c,Target t) {
        Entity target=entity(t.entity());if(!(target instanceof LivingEntity living)||!RpgEntityAdapters.allows(entity(c.caster()),living,RpgEntityAdapters.Effect.STATUS))return;
        int duration=MythicEngine.ticks(n(line,"duration","d",100,c,t),1,72000);String name=auraName(line.parameters());
        if(name.isBlank())throw new IllegalArgumentException("Damage aura needs an auraName");
        Set<String> causes=new HashSet<>();String mods=line.value("damagemods","");
        for(String item:mods.split(","))if(!item.isBlank()){String[] parts=item.trim().split("\\s+");if(parts.length!=2||number(parts[1],c,t)!=0)throw new IllegalArgumentException("Unsupported nonzero damage modifier");causes.add(parts[0].toUpperCase(Locale.ROOT));}
        damageWards.put(new AuraKey(target.getUuid(),name),new DamageWard(tick+duration,bool(line,"ce",bool(line,"cancelevent",false)),Set.copyOf(causes)));
    }
    private void move(Entity actor,Entity target,Vec3d velocity,String mode){
        if(target instanceof LivingEntity living&&!RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.MOVEMENT))return;
        if(velocity.lengthSquared()>64)throw new IllegalArgumentException("Movement exceeds skill limit");
        if(mode.equalsIgnoreCase("ADD"))velocity=velocity.add(target.getVelocity());else if(!mode.equalsIgnoreCase("SET"))throw new IllegalArgumentException("Unsupported velocity mode");
        target.setVelocity(velocity);target.velocityModified=true;target.velocityDirty=true;if(target instanceof ServerPlayerEntity player)player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }
    private void variable(MythicLine action,Context c,Target t){
        String name=action.value("var",action.value("variable","skill.value"));int separator=name.indexOf('.');String scope=separator<0?"skill":name.substring(0,separator);String key=separator<0?name:name.substring(separator+1);
        Map<String,String> map=switch(scope){case "skill"->c.variables();case "caster"->variables.computeIfAbsent(c.caster(),k->new HashMap<>());case "target"->{if(t.entity()==null)throw new IllegalArgumentException("Target variable requires an entity");yield variables.computeIfAbsent(t.entity(),k->new HashMap<>());}default->throw new IllegalArgumentException("Unsupported variable scope "+scope);};
        if(key.isBlank()||key.length()>128||map.size()>256&&!map.containsKey(key))throw new IllegalArgumentException("Variable limit exceeded");
        String value=text(action.value("value",action.value("v","0")),c,t);
        if(action.mechanic().equals("variableadd")||action.mechanic().equals("variablesubtract")){double result=number(map.getOrDefault(key,"0"),c,t)+(action.mechanic().equals("variableadd")?1:-1)*number(value,c,t);value=Double.toString(result);}
        else if(action.value("type","FLOAT").equalsIgnoreCase("FLOAT"))value=Double.toString(number(value,c,t));
        else if(action.value("type","FLOAT").equalsIgnoreCase("INTEGER"))value=Long.toString((long)number(value,c,t));
        else if(!action.value("type","STRING").equalsIgnoreCase("STRING"))throw new IllegalArgumentException("Unsupported variable type");
        map.put(key,value);
    }
    private void particles(MythicLine line,Context c,Target t,ServerWorld world,Point center) {
        boolean ring=line.mechanic().equals("effect:particlering");
        int amount=MythicEngine.ticks(n(line,"amount","a",n(line,"count",ring?1:10,c,t),c,t),0,4096),perPoint=ring?amount:1;
        if(ring){int points=MythicEngine.ticks(n(line,"points",32,c,t),1,256);amount=points*perPoint;}
        amount=Math.min(amount,Math.max(0,particleBudget));particleBudget-=amount;if(amount==0||perPoint==0)return;
        int color=Integer.parseInt(line.value("color",line.value("c","ffffff")).replace("#",""),16);float size=(float)n(line,"size",1,c,t);
        var particle=NativeParticles.effect(line.value("particle",line.value("p","end_rod")),color,size,line.value("material","stone"));double y=n(line,"y",0,c,t),speed=n(line,"speed",0,c,t);
        if(line.mechanic().equals("effect:particlering")||line.mechanic().equals("effect:particlesphere")) {
            double radius=n(line,"radius","r",3,c,t);if(radius<0||radius>64)throw new IllegalArgumentException("Invalid particle radius");
            int points=ring?Math.max(1,amount/perPoint):amount;
            for(int i=0;i<points;i++){double a=2*Math.PI*i/points,dy=0,r=radius;if(line.mechanic().equals("effect:particlesphere")){dy=1-2*(i+.5)/points;r=radius*Math.sqrt(1-dy*dy);a=i*Math.PI*(3-Math.sqrt(5));dy*=radius;}world.spawnParticles(particle,center.x()+Math.cos(a)*r,center.y()+y+dy,center.z()+Math.sin(a)*r,perPoint,n(line,"hs","hspread",0,c,t),n(line,"vs","vspread",0,c,t),n(line,"hs","hspread",0,c,t),speed);}
        }else {
            Entity caster=entity(c.caster());Vec3d direction=caster.getRotationVec(1).multiply(1,0,1).normalize(),side=new Vec3d(-direction.z,0,direction.x);
            Vec3d offset=direction.multiply(n(line,"forwardoffset",0,c,t)).add(side.multiply(n(line,"sideoffset",0,c,t)));
            double hs=n(line,"hs","hspread",.2,c,t),vs=n(line,"vs","vspread",.2,c,t);
            if(hs<0||vs<0||hs>64||vs>64)throw new IllegalArgumentException("Invalid particle spread");world.spawnParticles(particle,center.x()+offset.x,center.y()+y,center.z()+offset.z,amount,hs,vs,hs,speed);
        }
    }
    private static String auraName(Map<String,String> p){return p.getOrDefault("auraname",p.getOrDefault("an",p.getOrDefault("a",p.getOrDefault("aur",""))));}
    private void aura(MythicLine line,Context c,Target t,MythicEngine engine) {
        Entity target=entity(t.entity());if(!(target instanceof LivingEntity living)||!RpgEntityAdapters.allows(entity(c.caster()),living,RpgEntityAdapters.Effect.STATUS))return;
        String name=auraName(line.parameters());if(name.isBlank())throw new IllegalArgumentException("Aura requires a name");AuraKey key=new AuraKey(target.getUuid(),name);Aura previous=auras.get(key);
        int duration=MythicEngine.ticks(n(line,"duration","d",100,c,t),1,720000),max=MythicEngine.ticks(n(line,"maxstacks",1,c,t),1,256),added=MythicEngine.ticks(n(line,"stacks",1,c,t),1,256);
        if(auras.size()>=2048&&previous==null)throw new IllegalStateException("Aura capacity exceeded");int stacks=Math.min(max,(previous==null?0:previous.stacks())+added);
        long until=previous!=null&&!bool(line,"refreshduration",true)?previous.until():tick+duration;Aura aura=new Aura(stacks,until,c.target(t),line);auras.put(key,aura);
        if(previous==null)callback(line.value("onstart",""),c,t,point(target),engine);
        auraTick(key,aura,engine);
    }
    private void auraTick(AuraKey key,Aura expected,MythicEngine engine) {
        int period=MythicEngine.ticks(n(expected.action(),"interval","i",20,expected.context(),expected.context().target()),1,72000);
        int delay=(int)Math.max(1,Math.min(period,expected.until()-tick));engine.schedule(delay,expected.context(),()->{
            if(auras.get(key)!=expected)return;Entity target=entity(key.entity());
            if(target==null||!target.isAlive()){auras.remove(key,expected);return;}
            if(tick>=expected.until()){auras.remove(key,expected);callback(expected.action().value("onend",""),expected.context(),expected.context().target(),point(target),engine);return;}
            callback(expected.action().value("ontick",""),expected.context(),expected.context().target(),point(target),engine);auraTick(key,expected,engine);
        });
    }
    private void callback(String skill,Context context,Target target,Point origin,MythicEngine engine){if(!skill.isBlank())engine.call(skill,context.at(origin).child(target,Map.of()));}
    private void projectile(MythicLine line,Context c,Target target,MythicEngine engine) {
        Entity actor=entity(c.caster());double speed=n(line,"velocity","v",20,c,target)/20,range=n(line,"maxrange","mr",30,c,target),horizontal=n(line,"hr",1,c,target),vertical=n(line,"vr",1,c,target);
        if(speed<=0||speed>8||range<=0||range>128||horizontal<0||vertical<0||horizontal>8||vertical>8)throw new IllegalArgumentException("Invalid projectile geometry");
        Point start=point(actor).add(0,n(line,"syo",1.5,c,target),0),aim=position(target,c).add(0,n(line,"tyo",0,c,target),0);
        Vec3d direction=vector(aim).subtract(vector(start)).normalize();if(direction.lengthSquared()<.01)direction=actor.getRotationVec(1);
        var flight=new Flight(line,c,start,direction.multiply(speed),range,horizontal,vertical,new HashSet<>(),0,0);
        callback(line.value("onstart",""),c,target,start,engine);engine.schedule(1,c,()->flight(flight,engine));
    }
    private record Flight(MythicLine line,Context context,Point point,Vec3d velocity,double maxRange,double hr,double vr,Set<UUID> hit,double traveled,int age) { }
    private void flight(Flight f,MythicEngine engine) {
        Entity actor=entity(f.context().caster());ServerWorld world=world(f.point());if(actor==null||world==null)return;
        Vec3d from=vector(f.point()),to=from.add(f.velocity());double next=f.traveled()+f.velocity().length();boolean end=next>=f.maxRange()||f.age()>600;
        HitResult block=world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,actor));
        if(bool(f.line(),"sb",true)&&block.getType()!=HitResult.Type.MISS){to=block.getPos();end=true;}
        if(!world.isChunkLoaded(BlockPos.ofFloored(to))||!world.getWorldBorder().contains(to.x,to.z))end=true;
        Point origin=new Point(f.point().world(),to.x,to.y,to.z);Target location=new Target(null,origin);
        int interval=MythicEngine.ticks(n(f.line(),"interval","i",1,f.context(),location),1,200);
        if(f.age()%interval==0)callback(f.line().value("ontick",""),f.context(),location,origin,engine);
        List<LivingEntity> candidates=world.getEntitiesByClass(LivingEntity.class,new Box(from,to).expand(f.hr(),f.vr(),f.hr()),living->living!=actor&&!f.hit().contains(living.getUuid())&&living.isAlive()&&!actor.isTeammate(living)&&bool(f.line(),living instanceof ServerPlayerEntity?"hp":"hnp",true)&&RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.DAMAGE));
        Vec3d destination=to;candidates.sort(Comparator.comparingDouble(living->living.getBoundingBox().expand(f.hr(),f.vr(),f.hr()).raycast(from,destination).map(from::squaredDistanceTo).orElse(Double.MAX_VALUE)));
        for(LivingEntity hit:candidates) {
            var collision=hit.getBoundingBox().expand(f.hr(),f.vr(),f.hr()).raycast(from,to);if(collision.isEmpty()&&!hit.getBoundingBox().expand(f.hr(),f.vr(),f.hr()).contains(from))continue;
            f.hit().add(hit.getUuid());Vec3d hitAt=collision.orElse(from);Point impact=new Point(origin.world(),hitAt.x,hitAt.y,hitAt.z);
            callback(f.line().value("onhit",""),f.context(),new Target(hit.getUuid(),null),impact,engine);
            if(bool(f.line(),"se",true)){origin=impact;end=true;break;}
        }
        if(end){callback(f.line().value("onend",""),f.context(),new Target(null,origin),origin,engine);return;}
        Vec3d velocity=f.velocity().add(0,-n(f.line(),"gravity","g",0,f.context(),location),0);
        Flight nextFlight=new Flight(f.line(),f.context(),origin,velocity,f.maxRange(),f.hr(),f.vr(),f.hit(),next,f.age()+1);engine.schedule(1,f.context(),()->flight(nextFlight,engine));
    }
    @Override public List<String> validate(MythicDefinition definition) {
        List<String> errors=new ArrayList<>();List<MythicLine.Condition> conditions=new ArrayList<>(definition.conditions());conditions.addAll(definition.targetConditions());conditions.addAll(definition.triggerConditions());
        for(MythicLine action:definition.skills()) {
            Set<String> fields=ACTIONS.get(action.mechanic());if(fields==null){errors.add("Unsupported mechanic "+action.mechanic());continue;}
            for(String parameter:action.parameters().keySet())if(!fields.contains(parameter)&&!action.mechanic().equals("skill"))errors.add(action.mechanic()+": unsupported parameter "+parameter);
            if(Set.of("damage","mmodamage").contains(action.mechanic())) {
                if(!Set.of("SKILL","MAGIC","PHYSICAL").contains(action.value("type","SKILL").toUpperCase(Locale.ROOT)))errors.add("Unsupported damage type "+action.value("type",""));
                if(!Set.of("","sonic_boom","magic","lightning","lightning_bolt","custom","generic").contains(action.value("damagecause",action.value("dc","")).toLowerCase(Locale.ROOT)))errors.add("Unsupported damage cause");
            }
            if(action.mechanic().equals("projectile")) {
                for(String key:List.of("ho","hfs"))if(!action.value(key,"0").equals("0"))errors.add("Unsupported projectile "+key+"="+action.value(key,""));
                if(!action.value("hs","false").equalsIgnoreCase("false"))errors.add("Unsupported homing projectile");
            }
            if(action.mechanic().equals("lightning")&&!action.value("d",action.value("damage","0")).equals("0"))errors.add("Nonzero lightning damage needs an explicit gated damage graph");
            if(action.mechanic().equals("ondamaged")&&!action.value("damagemods","").isBlank())for(String item:action.value("damagemods","").split(","))if(!item.trim().matches("[A-Za-z_]+ +0(?:\\.0+)?"))errors.add("Unsupported damage modifier "+item);
            if(action.mechanic().equals("potion")&&!Registries.STATUS_EFFECT.containsId(potionId(action.value("type","slowness"))))errors.add("Unknown potion "+action.value("type",""));
            if(!action.trigger().isEmpty())errors.add("Trigger inside meta-skill "+action.trigger());
            if(action.selector()!=null) {
                if(!SELECTORS.contains(action.selector().name()))errors.add("Unsupported targeter "+action.selector().name());
                for(String key:action.selector().parameters().keySet())if(!Set.of("r","limit","sort","f","y","lockpitch","sideoffset").contains(key))errors.add("Unsupported targeter parameter "+key);
                if(!Set.of("NONE","NEAREST","FURTHEST","RANDOM").contains(action.selector().parameters().getOrDefault("sort","NONE").toUpperCase(Locale.ROOT)))errors.add("Unsupported target sorting");
                conditions.addAll(action.selector().conditions());
            }
        }
        for(var condition:conditions)if(!CONDITIONS.contains(condition.name()))errors.add("Unsupported condition "+condition.name());
        return List.copyOf(errors);
    }
}
