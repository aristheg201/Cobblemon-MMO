package vn.svframe.svframelib.skill;

import vn.svframe.svframelib.api.event.PlayerAttackEvent;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframelib.player.PlayerMetadata;
import vn.svframe.svframelib.script.util.VariableNotFoundException;
import vn.svframe.svframelib.script.variable.Variable;
import vn.svframe.svframelib.script.variable.VariableList;
import vn.svframe.svframelib.script.variable.VariableScope;
import vn.svframe.svframelib.script.variable.def.*;
import vn.svframe.svframelib.util.Lazy;
import vn.svframe.svframelib.util.Position;
import vn.svframe.svframelib.util.SkillOrientation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native Fabric cast context with SVFrameLib 1.7.1 variable-resolution semantics. */
public class SkillMetadata {
    public final SkillOrientation orientation;
    public static final List<String> RESERVED_VARIABLE_NAMES = List.of(
            "modifier","parameter","source","targetLocation","target_location","targetloc","targetl","targetLoc","target_loc",
            "caster","attack","random","rand","rdm","stat","target","var");
    public static final Pattern INTERNAL_PLACEHOLDER_PATTERN = Pattern.compile("<([^#&|!=<>]+)>");

    private final Skill cast;
    private final PlayerMetadata caster;
    private final VariableList vars;
    private final Vec3d sourceLocation;
    private final Vec3d targetLocation;
    private final Entity targetEntity;
    private final AttackMetadata attackSource;
    private final Object sourceEvent;

    public SkillMetadata(Skill cast, MMOPlayerData data){this(cast,data,null,null);}
    public SkillMetadata(Skill cast, MMOPlayerData data, Entity target, Vec3d targetLocation){
        this(cast,new PlayerMetadata(data),new VariableList(VariableScope.SKILL),data.isOnline()?data.getPlayer().getPos():Vec3d.ZERO,targetLocation,target,null,null,null);
    }
    public SkillMetadata(Skill cast, PlayerMetadata caster, Entity target, Vec3d source, Vec3d targetLocation, SkillOrientation orientation, AttackMetadata attack){
        this(cast,caster,new VariableList(VariableScope.SKILL),source,targetLocation,target,orientation,attack,null);
    }
    public SkillMetadata(Skill cast, PlayerMetadata caster, VariableList vars, Vec3d source, Vec3d targetLocation, Entity target, SkillOrientation orientation, AttackMetadata attack, Object sourceEvent){
        this.cast=cast;this.caster=Objects.requireNonNull(caster,"caster");this.vars=vars==null?new VariableList(VariableScope.SKILL):vars;
        this.sourceLocation=Objects.requireNonNull(source,"source");this.targetLocation=targetLocation;this.targetEntity=target;this.orientation=orientation;this.attackSource=attack;this.sourceEvent=sourceEvent;
    }

    public Skill getCast(){return cast;} public VariableList getVariableList(){return vars;} public PlayerMetadata getCaster(){return caster;}
    public Vec3d getSourceLocation(){return sourceLocation;} public boolean hasAttackSource(){return attackSource!=null;}
    public AttackMetadata getAttackSource(){return Objects.requireNonNull(attackSource,"Skill was not triggered by any attack");}
    public double getParameter(String id){return cast==null?0d:caster.getData().getSkillModifierMap().calculateValue(cast,id);}
    public Entity getTargetEntity(){return Objects.requireNonNull(targetEntity,"Skill has no target entity");} public Entity getTargetEntityOrNull(){return targetEntity;}
    public LivingEntity getTargetLivingEntityOrNull(){return targetEntity instanceof LivingEntity l?l:null;} public boolean hasTargetEntity(){return targetEntity!=null;}
    public Vec3d getTargetLocation(){return Objects.requireNonNull(targetLocation,"Skill has no target location");} public Vec3d getTargetLocationOrNull(){return targetLocation;} public boolean hasTargetLocation(){return targetLocation!=null;}
    public SkillOrientation getOrientation(){return Objects.requireNonNull(orientation,"Skill has no orientation");} public SkillOrientation getOrientationOrNull(){return orientation;} public Object getSourceEvent(){return sourceEvent;} public boolean hasOrientation(){return orientation!=null;}
    public Vec3d getSkillLocation(boolean source){return source?sourceLocation:targetLocation!=null?targetLocation:targetEntity!=null?targetEntity.getPos():sourceLocation;}
    public Entity getSkillEntity(boolean casterPriority){return casterPriority||targetEntity==null?caster.getPlayer():targetEntity;} public SkillOrientation getSkillOrientation(){return orientation;}

    private SkillMetadata copy(Skill next,PlayerMetadata nextCaster,VariableList nextVars,Vec3d source,Vec3d targetLoc,Entity target,SkillOrientation nextOrientation){
        return new SkillMetadata(next,nextCaster,nextVars,source,targetLoc,target,nextOrientation,attackSource,sourceEvent);
    }
    public SkillMetadata clone(Vec3d target){return copy(cast,caster,vars,sourceLocation,target,targetEntity,orientation);}
    public SkillMetadata clone(Skill next){return copy(next,caster,vars,sourceLocation,targetLocation,targetEntity,orientation);}
    public SkillMetadata withCaster(PlayerMetadata next){return copy(cast,next,vars,sourceLocation,targetLocation,targetEntity,orientation);}
    public SkillMetadata withOrigin(Vec3d next){return copy(cast,caster,vars,next,targetLocation,targetEntity,orientation);}
    public SkillMetadata withTargetEntity(Entity next){return copy(cast,caster,vars,sourceLocation,targetLocation,next,orientation);}
    public SkillMetadata withTargetLocation(Vec3d next){return copy(cast,caster,vars,sourceLocation,next,targetEntity,orientation);}
    public SkillMetadata clone(Vec3d source,Vec3d targetLoc,Entity target){return copy(cast,caster,vars,source,targetLoc,target,orientation);}
    public SkillMetadata clone(Vec3d source,Vec3d targetLoc,Entity target,SkillOrientation orient){return copy(cast,caster,vars,source,targetLoc,target,orient);}
    public SkillMetadata clone(Skill next,Vec3d source,Vec3d targetLoc,Entity target,SkillOrientation orient){return copy(next,caster,vars,source,targetLoc,target,orient);}

    public Variable<?> getUserVariable(String path){
        Variable<?> found=vars.getVariable(path); if(found!=null)return found;
        MMOPlayerData data=caster.getData();
        if(data.isPlaying()){found=data.getProfileSession().getVariableList().getVariable(path);if(found!=null)return found;}
        found=data.getVariableList().getVariable(path);if(found!=null)return found;
        found=VariableList.SERVER.getVariable(path);if(found!=null)return found;
        throw new VariableNotFoundException(path);
    }

    public Variable<?> getVariable(String path){
        Objects.requireNonNull(path,"path"); String[] parts=path.split("\\."); if(parts.length==0)throw new VariableNotFoundException(path);
        int index=1; Variable<?> variable;
        switch(parts[0]){
            case "modifier","parameter" -> {if(parts.length<=1)throw new IllegalArgumentException("Please specify a modifier name");variable=new DoubleVariable("temp",getParameter(parts[index++]));}
            case "source" -> variable=new PositionVariable("temp",new Position((ServerWorld)caster.getPlayer().getWorld(),sourceLocation));
            case "targetLocation","target_location","targetloc","targetl","targetLoc","target_loc" -> variable=new PositionVariable("temp",new Position((ServerWorld)caster.getPlayer().getWorld(),getTargetLocation()));
            case "caster" -> variable=new PlayerVariable("temp",caster.getPlayer());
            case "attack" -> variable=new AttackMetadataVariable("temp",getAttackSource());
            case "random","rand","rdm" -> variable=RandomVariable.INSTANCE;
            case "stat" -> variable=new StatsVariable("temp",caster);
            case "target" -> {Entity t=getTargetEntity();variable=t instanceof ServerPlayerEntity p?new PlayerVariable("temp",p):new EntityVariable("temp",t);}
            case "var" -> {if(parts.length<=1)throw new IllegalArgumentException("User variable name is not specified; use the variable name directly");variable=getUserVariable(parts[index++]);}
            default -> variable=getUserVariable(parts[0]);
        }
        while(index<parts.length){try{variable=variable.getVariable(parts[index]);}catch(RuntimeException e){throw new VariableNotFoundException(path,parts,index);}index++;}
        return variable;
    }

    public void setVariable(String key,Object value){if(value==null)return;vars.registerVariable(asVariable(key,value));}
    private static Variable<?> asVariable(String key,Object value){
        if(value instanceof Variable<?> v)return v;
        if(value instanceof Integer i)return new IntegerVariable(key,i);
        if(value instanceof Number n)return new DoubleVariable(key,n.doubleValue());
        if(value instanceof Boolean b)return new BooleanVariable(key,b);
        if(value instanceof String s)return new StringVariable(key,s);
        if(value instanceof ServerPlayerEntity p)return new PlayerVariable(key,p);
        if(value instanceof Entity e)return new EntityVariable(key,e);
        return new StringVariable(key,String.valueOf(value));
    }

    public String parseString(String input){
        if(input==null)return ""; Matcher m=INTERNAL_PLACEHOLDER_PATTERN.matcher(input);StringBuffer out=new StringBuffer(input.length());
        while(m.find()){String replacement;try{replacement=String.valueOf(getVariable(m.group(1)));}catch(RuntimeException ignored){continue;}m.appendReplacement(out,Matcher.quoteReplacement(replacement));}
        m.appendTail(out);return out.toString();
    }

    public static SkillMetadata of(MMOPlayerData data){return of(data,EquipmentSlot.MAIN_HAND);} public static SkillMetadata of(MMOPlayerData data,EquipmentSlot hand){return of(data,hand,null,null,null,null,null,null);}
    public static Lazy<SkillMetadata> lazyOf(MMOPlayerData data){return Lazy.of(()->of(data));}
    public static SkillMetadata of(MMOPlayerData data,Entity target){return of(data,EquipmentSlot.MAIN_HAND,null,target,null,null,null,null);}
    public static SkillMetadata of(PlayerMetadata caster){return of(caster.getData(),caster.getActionHand(),null,null,null,null,caster,null);}
    public static SkillMetadata of(PlayerMetadata caster,Entity target){return of(caster.getData(),caster.getActionHand(),null,target,null,null,caster,null);}
    public static Lazy<SkillMetadata> lazyOf(PlayerMetadata caster,Entity target){return Lazy.of(()->of(caster,target));}
    public static SkillMetadata of(MMOPlayerData data,Vec3d targetLocation){return of(data,EquipmentSlot.MAIN_HAND,null,null,targetLocation,null,null,null);}
    public static SkillMetadata of(MMOPlayerData data,Vec3d source,Vec3d targetLocation){return of(data,EquipmentSlot.MAIN_HAND,source,null,targetLocation,null,null,null);}
    public static SkillMetadata of(PlayerAttackEvent event){Objects.requireNonNull(event,"event");return of(event.getAttacker(),event.getAttack().getTarget(),event.getAttack(),event);}
    public static Lazy<SkillMetadata> lazyOf(PlayerAttackEvent event){return Lazy.of(()->of(event));}
    public static SkillMetadata of(PlayerMetadata caster,Entity target,AttackMetadata attack,Object sourceEvent){return of(caster.getData(),caster.getActionHand(),null,target,null,attack,caster,sourceEvent);}
    public static SkillMetadata of(MMOPlayerData data,EquipmentSlot hand,Vec3d source,Entity target,Vec3d targetLocation,AttackMetadata attack,PlayerMetadata cached,Object sourceEvent){
        Objects.requireNonNull(data,"data");EquipmentSlot actual=Objects.requireNonNullElse(hand,EquipmentSlot.MAIN_HAND);PlayerMetadata pm=cached!=null?cached:data.getStatMap().cache(actual);Vec3d origin=source!=null?source:data.getPlayer().getPos();
        return new SkillMetadata(null,pm,new VariableList(VariableScope.SKILL),origin,targetLocation,target,null,attack,sourceEvent);
    }

    public AttackMetadata attack(LivingEntity target,double damage,DamageType... types){return caster.attack(target,damage,types);}
    public boolean hasAttackBound(){return attackSource!=null;} public AttackMetadata getAttack(){return getAttackSource();}
    public Variable<?> getReference(String path){return getVariable(path);} public Variable<?> getCustomVariable(String path){return getUserVariable(path);} public double getModifier(String key){return getParameter(key);}
    @Override public String toString(){return "SkillMetadata{"+(cast==null?"none":cast)+",caster="+caster.getData().getUniqueId()+"}";}
}
