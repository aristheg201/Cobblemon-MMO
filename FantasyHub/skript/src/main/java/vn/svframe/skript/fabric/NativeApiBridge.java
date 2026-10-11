package vn.svframe.skript.fabric;

import java.util.*;
import java.util.regex.*;
import net.minecraft.entity.*;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import vn.svframe.skript.lang.*;
import vn.svframe.skript.lang.Platform.*;
import vn.svframe.skript.lang.Values.*;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframelib.fabric.runtime.NativePlaceholderRegistry;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.SVFrameMMO;

/** Named legacy API adapters, implemented by native Fabric APIs. Never loads Bukkit or arbitrary Java classes. */
public final class NativeApiBridge {
    private final FabricPlatform platform;
    NativeApiBridge(FabricPlatform platform){this.platform=platform;}
    private record Type(String name){}
    private record Data(EntityRef player){}
    private record Stats(EntityRef player){}
    private record Event(Context context){}
    public record Vector(double x,double y,double z){
        Vec3d vec(){return new Vec3d(x,y,z);}
        static Vector of(Vec3d v){return new Vector(v.x,v.y,v.z);}
    }
    private static final Map<String,String> IMPORTS=Map.ofEntries(
        Map.entry("org.bukkit.event.player.PlayerMoveEvent","PlayerMoveEvent"),
        Map.entry("com.destroystokyo.paper.event.player.PlayerJumpEvent","PlayerJumpEvent"),
        Map.entry("org.bukkit.util.Vector","Vector"),
        Map.entry("net.Indyuce.mmocore.api.event.PlayerEnterCastingModeEvent","PlayerEnterCastingModeEvent"),
        Map.entry("net.Indyuce.mmocore.api.event.PlayerExitCastingModeEvent","PlayerExitCastingModeEvent"),
        Map.entry("net.Indyuce.mmocore.api.player.PlayerData","PlayerData"),
        Map.entry("io.lumine.mythic.lib.player.resource.ResourceUpdateReason","ResourceUpdateReason"),
        Map.entry("org.bukkit.Particle","Particle"),Map.entry("org.bukkit.Sound","Sound"));
    private static final Set<String> PARTICLES=Set.of("CLOUD","END_ROD","ENCHANTMENT_TABLE","CRIT","SMOKE_LARGE");
    private static ParticleEffect particle(String name){return switch(name){case "CLOUD"->ParticleTypes.CLOUD;case "END_ROD"->ParticleTypes.END_ROD;case "ENCHANTMENT_TABLE"->ParticleTypes.ENCHANT;case "CRIT"->ParticleTypes.CRIT;case "SMOKE_LARGE"->ParticleTypes.LARGE_SMOKE;default->throw new IllegalArgumentException("Unknown particle "+name);};}
    private static final Map<String,Set<Integer>> METHODS=Map.ofEntries(
        Map.entry("has",Set.of(1)),Map.entry("get",Set.of(1)),Map.entry("getPlayer",Set.of(0)),Map.entry("getFrom",Set.of(0)),Map.entry("getTo",Set.of(0)),
        Map.entry("setCancelled",Set.of(1)),Map.entry("isCancelled",Set.of(0)),Map.entry("isCasting",Set.of(0)),Map.entry("getStamina",Set.of(0)),Map.entry("getStats",Set.of(0)),Map.entry("getStat",Set.of(1)),Map.entry("setStamina",Set.of(2)),
        Map.entry("getLocation",Set.of(0)),Map.entry("getDirection",Set.of(0)),Map.entry("getWorld",Set.of(0)),Map.entry("getX",Set.of(0)),Map.entry("getY",Set.of(0)),Map.entry("getZ",Set.of(0)),
        Map.entry("setX",Set.of(1)),Map.entry("setY",Set.of(1)),Map.entry("setZ",Set.of(1)),Map.entry("normalize",Set.of(0)),Map.entry("clone",Set.of(0)),Map.entry("multiply",Set.of(1)),Map.entry("dot",Set.of(1)),Map.entry("add",Set.of(1)),Map.entry("subtract",Set.of(1)),Map.entry("toVector",Set.of(0)),Map.entry("intValue",Set.of(0)),
        Map.entry("getHealth",Set.of(0)),Map.entry("getMaxHealth",Set.of(0)),Map.entry("setHealth",Set.of(1)),Map.entry("damage",Set.of(1,2)),Map.entry("setVelocity",Set.of(1)),
        Map.entry("isSprinting",Set.of(0)),Map.entry("isSneaking",Set.of(0)),Map.entry("playSound",Set.of(4)),Map.entry("spawnParticle",Set.of(6)),Map.entry("getUniqueId",Set.of(0)));
    public boolean supportsImport(String qualified){return IMPORTS.containsKey(qualified);}
    public Expression expression(String source,ExpressionCompiler compiler){
        if(source.equals("event"))return Event::new;
        if(IMPORTS.containsValue(source))return c->new Type(source);
        Matcher constructor=Pattern.compile("^new Vector\\((.*)\\)$").matcher(source);
        if(constructor.matches()){List<Expression> args=arguments(constructor.group(1),compiler);if(args.size()!=3)throw new Syntax.Error(compiler.source(),"Vector requires x, y, z");return c->new Vector(n(args.get(0).evaluate(c)),n(args.get(1).evaluate(c)),n(args.get(2).evaluate(c)));}
        int dot=Syntax.find(source,".",true);if(dot<=0)return naturalExpression(source,compiler);
        String receiver=source.substring(0,dot),tail=source.substring(dot+1);Matcher call=Pattern.compile("^(\\w+)\\((.*)\\)$").matcher(tail);
        if(!call.matches()){
            if(receiver.equals("Particle")){if(!PARTICLES.contains(tail))throw new Syntax.Error(compiler.source(),"Particle adapter unavailable: "+tail);return c->particle(tail);}
            if(receiver.equals("ResourceUpdateReason")){try{ResourceUpdateReason reason=ResourceUpdateReason.valueOf(tail);return c->reason;}catch(IllegalArgumentException e){throw new Syntax.Error(compiler.source(),"Unknown resource update reason "+tail);}}
            return naturalExpression(source,compiler);
        }
        String method=call.group(1);if(!METHODS.containsKey(method))throw new Syntax.Error(compiler.source(),"Native API method unavailable: "+method);
        List<Expression> args=arguments(call.group(2),compiler);if(!METHODS.get(method).contains(args.size()))throw new Syntax.Error(compiler.source(),"Wrong argument count for "+method);
        Expression object=compiler.compile(receiver);
        return c->{Object target=object.evaluate(c);List<Object> parameters=args.stream().map(a->a.evaluate(c)).toList();Object result=invoke(target,method,parameters,c);if(result!=null&&(target instanceof Vector||target instanceof Location)&&Set.of("setX","setY","setZ","normalize","multiply","add","subtract").contains(method)&&object instanceof Reference ref)ref.change(c,result,"set");return result;};
    }
    private List<Expression> arguments(String args,ExpressionCompiler compiler){return args.isBlank()?List.of():Syntax.split(args,",").stream().map(compiler::compile).toList();}
    private Expression naturalExpression(String source,ExpressionCompiler compiler){
        Matcher placeholder=Pattern.compile("(?i)^placeholder (.+)'s value for (.+)$").matcher(source);
        if(placeholder.matches()){Expression key=compiler.compile(placeholder.group(1)),player=compiler.compile(placeholder.group(2));return c->{ServerPlayerEntity p=platform.player(player.evaluate(c));if(p==null)return null;String id=c.text(key.evaluate(c));return NativePlaceholderRegistry.parse(p.getUuid(),id.startsWith("%")?id:"%"+id+"%");};}
        Matcher query=Pattern.compile("(?i)^living entities in radius (.+) around (.+)$").matcher(source);
        if(query.matches()){Expression radius=compiler.compile(query.group(1)),center=compiler.compile(query.group(2));return c->{double r=n(radius.evaluate(c));if(r<0||r>128)throw new IllegalArgumentException("Entity query radius must be between 0 and 128");Location loc=location(center.evaluate(c));if(loc==null)return List.of();ServerWorld world=platform.world(loc.world());if(world==null)return List.of();Vec3d point=position(loc);return world.getEntitiesByClass(LivingEntity.class,new Box(point,point).expand(r),e->e.isAlive()&&!e.isSpectator()&&e.squaredDistanceTo(point)<=r*r).stream().limit(1024).map(FabricPlatform::ref).toList();};}
        return null;
    }
    private Object invoke(Object target,String method,List<Object> args,Context context){
        if(target==null)return null;
        if(target instanceof Type type&&type.name.equals("PlayerData")){ServerPlayerEntity player=platform.player(args.getFirst());if(method.equals("has"))return player!=null&&SVFrameMMO.playerData().find(player.getUuid())!=null;if(method.equals("get"))return player==null?null:new Data(FabricPlatform.ref(player));}
        if(target instanceof Event event)return switch(method){case "getPlayer"->event.context.event("player");case "getFrom"->event.context.event("from");case "getTo"->event.context.event("to");case "setCancelled"->{if(Values.truth(args.getFirst()))event.context.cancel();else {if(event.context.delayed)throw new IllegalStateException("Cannot uncancel after delay");event.context.eventState.put("cancelled",false);}yield null;}case "isCancelled"->event.context.cancelled();default->unsupported(target,method);};
        if(target instanceof Data data){ServerPlayerEntity player=platform.player(data.player);if(player==null)return null;var profile=SVFrameMMO.playerData().get(player);return switch(method){case "isCasting"->SVFrameMMO.skillBar().isCasting(player.getUuid());case "getStamina"->profile.getStamina();case "getStats"->new Stats(data.player);case "setStamina"->{if(!(args.get(1) instanceof ResourceUpdateReason reason))throw new IllegalArgumentException("Expected ResourceUpdateReason");double before=profile.getStamina();if(!profile.setStamina(n(args.getFirst()),reason))throw new IllegalStateException("Stamina update cancelled; dash aborted");SkriptQa.staminaChanged(player,before,profile.getStamina(),reason);yield null;}default->unsupported(target,method);};}
        if(target instanceof Stats stats&&method.equals("getStat")){ServerPlayerEntity player=platform.player(stats.player);return player==null?null:SVFrameMMO.playerData().get(player).getMMOPlayerData().getStatMap().getStat(context.text(args.getFirst()));}
        if(target instanceof Number number&&method.equals("intValue"))return (double)number.intValue();
        if(target instanceof Vector vector)return switch(method){case "getX"->vector.x;case "getY"->vector.y;case "getZ"->vector.z;case "clone"->new Vector(vector.x,vector.y,vector.z);case "normalize"->Vector.of(vector.vec().normalize());case "multiply"->Vector.of(vector.vec().multiply(n(args.getFirst())));case "dot"->vector.vec().dotProduct(vector(args.getFirst()).vec());case "add"->Vector.of(vector.vec().add(vector(args.getFirst()).vec()));case "subtract"->Vector.of(vector.vec().subtract(vector(args.getFirst()).vec()));case "setX"->new Vector(n(args.getFirst()),vector.y,vector.z);case "setY"->new Vector(vector.x,n(args.getFirst()),vector.z);case "setZ"->new Vector(vector.x,vector.y,n(args.getFirst()));default->unsupported(target,method);};
        if(target instanceof Location loc)return switch(method){case "getX"->loc.x();case "getY"->loc.y();case "getZ"->loc.z();case "getWorld"->loc.world();case "clone"->new Location(loc.world(),loc.x(),loc.y(),loc.z(),loc.yaw(),loc.pitch());case "toVector"->new Vector(loc.x(),loc.y(),loc.z());case "getDirection"->Vector.of(Vec3d.fromPolar(loc.pitch(),loc.yaw()));case "add","subtract"->{Vec3d offset=vector(args.getFirst()).vec().multiply(method.equals("subtract")?-1:1);yield new Location(loc.world(),loc.x()+offset.x,loc.y()+offset.y,loc.z()+offset.z,loc.yaw(),loc.pitch());}default->unsupported(target,method);};
        if(target instanceof WorldRef worldRef&&method.equals("spawnParticle")){ServerWorld world=platform.world(worldRef);Location loc=location(args.get(1));if(world==null||loc==null||!loc.world().equals(worldRef))return null;if(!(args.getFirst() instanceof ParticleEffect effect))throw new IllegalArgumentException("Expected particle type");int count=(int)n(args.get(2));if(count<0||count>512)throw new IllegalArgumentException("Particle count outside 0..512");world.spawnParticles(effect,loc.x(),loc.y(),loc.z(),count,bounded(args.get(3),16),bounded(args.get(4),16),bounded(args.get(5),16),0);return null;}
        Entity entity=platform.entity(target);if(entity==null)return null;
        return switch(method){
            case "getLocation"->FabricPlatform.location(entity);case "getWorld"->new WorldRef(entity.getWorld().getRegistryKey().getValue().toString());case "getUniqueId"->entity.getUuid().toString();case "isSprinting"->entity.isSprinting();case "isSneaking"->entity.isSneaking();
            case "getHealth"->entity instanceof LivingEntity living?(double)living.getHealth():null;case "getMaxHealth"->entity instanceof LivingEntity living?(double)living.getMaxHealth():null;
            case "setHealth"->{if(!(entity instanceof LivingEntity living))throw new IllegalArgumentException("Health target is not living");health(living,n(args.getFirst()),actor(context,entity));yield null;}
            case "damage"->{if(!(entity instanceof LivingEntity living))throw new IllegalArgumentException("Damage target is not living");Entity actor=args.size()==2?platform.entity(args.get(1)):actor(context,entity);if(actor!=null&&RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.DAMAGE)){float amount=(float)Math.min(Float.MAX_VALUE,Math.max(0,n(args.getFirst())));living.damage(actor instanceof ServerPlayerEntity p?living.getDamageSources().playerAttack(p):actor instanceof LivingEntity a?living.getDamageSources().mobAttack(a):living.getDamageSources().generic(),amount);}yield null;}
            case "setVelocity"->{velocity(entity,vector(args.getFirst()).vec(),actor(context,entity));yield null;}
            case "playSound"->{if(!(entity instanceof ServerPlayerEntity player))throw new IllegalArgumentException("Player sound receiver required");Location loc=location(args.getFirst());if(loc!=null)sound(loc,context.text(args.get(1)),n(args.get(2)),n(args.get(3)),player);yield null;}
            default->unsupported(target,method);
        };
    }
    public Entity actor(Context c,Entity fallback){for(String key:List.of("_p","_owner","_caster","_ownerp","_ownerP")){Object value=c.locals.get(key);if(value instanceof EntityRef||value instanceof Entity)return platform.entity(value);}for(String key:List.of("attacker","player")){Object value=c.event(key);if(value instanceof EntityRef||value instanceof Entity)return platform.entity(value);}return fallback;}
    public void health(LivingEntity living,double requested,Entity actor){
        if(actor==null)return;if(!Double.isFinite(requested))throw new IllegalArgumentException("Invalid health");double health=Math.clamp(requested,0,living.getMaxHealth());
        if(health>living.getHealth()){RpgEntityAdapters.heal(actor,living,health-living.getHealth());return;}
        if(health>=living.getHealth()||!RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.DAMAGE))return;
        var source=actor instanceof ServerPlayerEntity p?living.getDamageSources().playerAttack(p):actor instanceof LivingEntity a?living.getDamageSources().mobAttack(a):living.getDamageSources().generic();
        if(RpgEntityAdapters.find(living)!=null){living.damage(source,(float)(living.getHealth()-health));return;}
        living.setHealth((float)health);if(health<=0)living.onDeath(source);
    }
    public void velocity(Entity entity,Vec3d velocity,Entity actor){if(actor==null)return;if(entity instanceof LivingEntity living&&!RpgEntityAdapters.allows(actor,living,RpgEntityAdapters.Effect.MOVEMENT))return;if(!Double.isFinite(velocity.lengthSquared())||velocity.lengthSquared()>1024)throw new IllegalArgumentException("Invalid script velocity");entity.setVelocity(velocity);entity.velocityModified=true;if(entity instanceof ServerPlayerEntity player)player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(entity));}
    public Location location(Object value){if(value instanceof Location loc)return loc;Entity entity=platform.entity(value);return entity==null?null:FabricPlatform.location(entity);}
    public static Vec3d position(Location loc){return new Vec3d(loc.x(),loc.y(),loc.z());}
    private Vector vector(Object value){if(value instanceof Vector v)return v;throw new IllegalArgumentException("Expected Vector");}
    private double n(Object value){double n=Values.number(value);if(!Double.isFinite(n))throw new IllegalArgumentException("Non-finite native API argument");return n;}
    private double bounded(Object value,double max){double n=n(value);if(Math.abs(n)>max)throw new IllegalArgumentException("Native API argument exceeds limit");return n;}
    private Object unsupported(Object target,String method){throw new IllegalArgumentException("Native API "+method+" unavailable on "+target.getClass().getSimpleName());}
    public void sound(Location loc,String name,double volume,double pitch,ServerPlayerEntity receiver){Identifier id=Identifier.tryParse(name);if(id==null||!Registries.SOUND_EVENT.containsId(id))throw new IllegalArgumentException("Unknown sound "+name);if(volume<0||volume>16||pitch<=0||pitch>4)throw new IllegalArgumentException("Invalid sound volume/pitch");SoundEvent event=Registries.SOUND_EVENT.get(id);if(receiver!=null)receiver.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(event),SoundCategory.PLAYERS,loc.x(),loc.y(),loc.z(),(float)volume,(float)pitch,receiver.getRandom().nextLong()));else {ServerWorld world=platform.world(loc.world());if(world!=null)world.playSound(null,loc.x(),loc.y(),loc.z(),event,SoundCategory.PLAYERS,(float)volume,(float)pitch);}}
    public Action effect(String source,ExpressionCompiler compiler){
        Matcher actionBar=Pattern.compile("(?i)^send action bar (.+) to (.+)$").matcher(source);
        if(actionBar.matches()){Expression text=compiler.compile(actionBar.group(1)),recipient=compiler.compile(actionBar.group(2));return c->{ServerPlayerEntity player=platform.player(recipient.evaluate(c));if(player!=null)player.sendMessage(Text.literal(c.text(text.evaluate(c)).replace('&','§')),true);};}
        Matcher sound=Pattern.compile("(?i)^play sound (.+) with volume (.+) and pitch (.+) at (.+)$").matcher(source);
        if(sound.matches()){Expression name=compiler.compile(sound.group(1)),volume=compiler.compile(sound.group(2)),pitch=compiler.compile(sound.group(3)),where=compiler.compile(sound.group(4));return c->{Location loc=location(where.evaluate(c));if(loc!=null)sound(loc,c.text(name.evaluate(c)),n(volume.evaluate(c)),n(pitch.evaluate(c)),null);};}
        Matcher pushUp=Pattern.compile("(?i)^push (.+) upwards at speed (.+)$").matcher(source);
        if(pushUp.matches()){Expression target=compiler.compile(pushUp.group(1)),speed=compiler.compile(pushUp.group(2));return c->{Entity entity=platform.entity(target.evaluate(c));if(entity!=null)velocity(entity,new Vec3d(0,n(speed.evaluate(c)),0),actor(c,entity));};}
        Matcher move=Pattern.compile("(?i)^(push|pull) (.+) (away from|towards) (.+) at speed (.+)$").matcher(source);
        if(move.matches()){Expression target=compiler.compile(move.group(2)),center=compiler.compile(move.group(4)),speed=compiler.compile(move.group(5));boolean away=move.group(3).equalsIgnoreCase("away from");return c->{Entity entity=platform.entity(target.evaluate(c));Location loc=location(center.evaluate(c));if(entity!=null&&loc!=null&&loc.world().id().equals(entity.getWorld().getRegistryKey().getValue().toString())){Vec3d direction=entity.getPos().subtract(position(loc)).normalize().multiply((away?1:-1)*n(speed.evaluate(c)));velocity(entity,direction,actor(c,entity));}};}
        int dot=Syntax.find(source,".",true);if(dot>0&&source.endsWith(")")){Expression call=expression(source,compiler);if(call!=null)return c->call.evaluate(c);}
        return null;
    }
}
