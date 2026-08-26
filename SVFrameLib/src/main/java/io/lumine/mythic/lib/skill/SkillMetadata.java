package io.lumine.mythic.lib.skill;

import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.damage.AttackMetadata;
import io.lumine.mythic.lib.player.PlayerMetadata;
import io.lumine.mythic.lib.util.Lazy;
import io.lumine.mythic.lib.util.SkillOrientation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkillMetadata {
    public final SkillOrientation orientation;
    public static final List<String> RESERVED_VARIABLE_NAMES = List.of("modifier", "parameter", "source", "targetLocation", "targetLoc", "target_loc", "target_location", "targetloc", "targetl", "caster", "attack", "stat", "target", "var", "rand", "random", "rdm");
    public static final Pattern INTERNAL_PLACEHOLDER_PATTERN = Pattern.compile("<([^#&|!=<>]+)>");

    private final Skill cast;
    private final PlayerMetadata caster;
    private final Entity targetEntity;
    private final Vec3d sourceLocation;
    private final Vec3d targetLocation;
    private final AttackMetadata attackSource;
    private final Map<String,Object> vars = new HashMap<>();

    public SkillMetadata(Skill cast, MMOPlayerData data) { this(cast, data, null, null); }
    public SkillMetadata(Skill cast, MMOPlayerData data, Entity target, Vec3d targetLocation) {
        this(cast, new PlayerMetadata(data), target, data.isOnline() ? data.getPlayer().getPos() : Vec3d.ZERO, targetLocation, null, null);
    }
    public SkillMetadata(Skill cast, PlayerMetadata caster, Entity target, Vec3d source, Vec3d targetLocation, SkillOrientation orientation, AttackMetadata attack) {
        this.cast = cast;
        this.caster = Objects.requireNonNull(caster, "caster");
        this.targetEntity = target;
        this.sourceLocation = Objects.requireNonNull(source, "source");
        this.targetLocation = targetLocation;
        this.orientation = orientation;
        this.attackSource = attack;
    }

    public Skill getCast(){return cast;}
    public PlayerMetadata getCaster(){return caster;}
    public Vec3d getSourceLocation(){return sourceLocation;}
    public boolean hasAttackSource(){return attackSource!=null;}
    public AttackMetadata getAttackSource(){return Objects.requireNonNull(attackSource,"Skill was not triggered by any attack");}
    public double getParameter(String id){return cast==null?0:caster.getData().getSkillModifierMap().calculateValue(cast,id);}
    public Entity getTargetEntity(){return Objects.requireNonNull(targetEntity,"Skill has no target entity");}
    public Entity getTargetEntityOrNull(){return targetEntity;}
    public LivingEntity getTargetLivingEntityOrNull(){return targetEntity instanceof LivingEntity living?living:null;}
    public boolean hasTargetEntity(){return targetEntity!=null;}
    public Vec3d getTargetLocation(){return Objects.requireNonNull(targetLocation,"Skill has no target location");}
    public Vec3d getTargetLocationOrNull(){return targetLocation;}
    public boolean hasTargetLocation(){return targetLocation!=null;}
    public SkillOrientation getOrientation(){return Objects.requireNonNull(orientation,"Skill has no orientation");}
    public SkillOrientation getOrientationOrNull(){return orientation;}
    public boolean hasOrientation(){return orientation!=null;}
    public Vec3d getSkillLocation(boolean source){return source?sourceLocation:targetLocation!=null?targetLocation:targetEntity!=null?targetEntity.getPos():sourceLocation;}
    public Entity getSkillEntity(boolean casterPriority){return casterPriority||targetEntity==null?caster.getPlayer():targetEntity;}
    public SkillOrientation getSkillOrientation(){return orientation;}

    public SkillMetadata clone(Vec3d target){return new SkillMetadata(cast,caster,targetEntity,sourceLocation,target,orientation,attackSource);}
    public SkillMetadata clone(Skill next){return new SkillMetadata(next,caster,targetEntity,sourceLocation,targetLocation,orientation,attackSource);}
    public SkillMetadata withCaster(PlayerMetadata next){return new SkillMetadata(cast,next,targetEntity,sourceLocation,targetLocation,orientation,attackSource);}
    public SkillMetadata withOrigin(Vec3d next){return new SkillMetadata(cast,caster,targetEntity,next,targetLocation,orientation,attackSource);}
    public SkillMetadata withTargetEntity(Entity next){return new SkillMetadata(cast,caster,next,sourceLocation,targetLocation,orientation,attackSource);}
    public SkillMetadata withTargetLocation(Vec3d next){return new SkillMetadata(cast,caster,targetEntity,sourceLocation,next,orientation,attackSource);}

    public String parseString(String input){
        if(input==null)return "";
        Matcher matcher=INTERNAL_PLACEHOLDER_PATTERN.matcher(input);
        StringBuffer output=new StringBuffer(input.length());
        while(matcher.find()){
            String key=matcher.group(1);
            Object value=resolveNumericOrObject(key);
            if(value==null) continue;
            matcher.appendReplacement(output,Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    /** Native compatibility variable lookup used by expression placeholders. */
    public Object getVariable(String key){return resolveNumericOrObject(key);}
    public void setVariable(String key,Object value){if(value==null)vars.remove(key);else vars.put(key,value);}

    private Object resolveNumericOrObject(String key){
        if(key==null||key.isBlank())return null;
        Object direct=vars.get(key);
        if(direct!=null)return direct;
        String[] path=key.split("\\.");
        if(path.length==0)return null;
        return switch(path[0]){
            case "modifier","parameter" -> path.length>1?getParameter(path[1]):null;
            case "stat" -> path.length>1?caster.getStat(path[1]):null;
            case "random","rand","rdm" -> path.length>1&&path[1].equalsIgnoreCase("double")?Math.random():null;
            case "source" -> coordinate(sourceLocation,path);
            case "targetLocation","targetLoc","target_loc","target_location","targetloc","targetl" -> targetLocation==null?null:coordinate(targetLocation,path);
            case "caster" -> casterValue(path);
            case "target" -> targetValue(path);
            case "var" -> path.length>1?vars.get(path[1]):null;
            default -> direct;
        };
    }

    private static Object coordinate(Vec3d location,String[] path){
        if(path.length<2)return location;
        return switch(path[1].toLowerCase(java.util.Locale.ROOT)){
            case "x" -> location.x;
            case "y" -> location.y;
            case "z" -> location.z;
            default -> null;
        };
    }

    private Object casterValue(String[] path){
        if(path.length<2)return caster.getPlayer();
        return switch(path[1].toLowerCase(java.util.Locale.ROOT)){
            case "x" -> caster.getPlayer().getX();
            case "y" -> caster.getPlayer().getY();
            case "z" -> caster.getPlayer().getZ();
            case "health","hp" -> caster.getPlayer().getHealth();
            default -> null;
        };
    }

    private Object targetValue(String[] path){
        if(targetEntity==null)return null;
        if(path.length<2)return targetEntity;
        return switch(path[1].toLowerCase(java.util.Locale.ROOT)){
            case "x" -> targetEntity.getX();
            case "y" -> targetEntity.getY();
            case "z" -> targetEntity.getZ();
            case "health","hp" -> targetEntity instanceof LivingEntity living?living.getHealth():null;
            default -> null;
        };
    }

    public static SkillMetadata of(MMOPlayerData data){return of(data,EquipmentSlot.MAIN_HAND);}
    public static SkillMetadata of(MMOPlayerData data, EquipmentSlot actionHand){return of(data,actionHand,null,null,null,null,null,null);}
    public static Lazy<SkillMetadata> lazyOf(MMOPlayerData data){return Lazy.of(() -> of(data));}
    public static SkillMetadata of(PlayerMetadata caster){return of(caster.getData(),caster.getActionHand(),null,null,null,null,caster,null);}
    public static SkillMetadata of(PlayerMetadata caster, Entity target){return of(caster.getData(),caster.getActionHand(),null,target,null,null,caster,null);}
    public static Lazy<SkillMetadata> lazyOf(PlayerMetadata caster, Entity target){return Lazy.of(() -> of(caster,target));}
    public static SkillMetadata of(MMOPlayerData data, Entity target){return of(data,EquipmentSlot.MAIN_HAND,null,target,null,null,null,null);}
    public static SkillMetadata of(MMOPlayerData data, Vec3d targetLocation){return of(data,EquipmentSlot.MAIN_HAND,null,null,targetLocation,null,null,null);}
    public static SkillMetadata of(MMOPlayerData data, Vec3d source, Vec3d targetLocation){return of(data,EquipmentSlot.MAIN_HAND,source,null,targetLocation,null,null,null);}

    /** Fabric equivalent of the full 1.7.1 metadata factory; sourceEvent is intentionally opaque on Fabric. */
    public static SkillMetadata of(MMOPlayerData data, EquipmentSlot actionHand, Vec3d source, Entity target, Vec3d targetLocation, AttackMetadata attack, PlayerMetadata cached, Object sourceEvent) {
        Objects.requireNonNull(data,"data");
        EquipmentSlot hand = Objects.requireNonNullElse(actionHand,EquipmentSlot.MAIN_HAND);
        PlayerMetadata playerMetadata = cached != null ? cached : data.getStatMap().cache(hand);
        Vec3d origin = source != null ? source : data.getPlayer().getPos();
        return new SkillMetadata(null,playerMetadata,target,origin,targetLocation,null,attack);
    }

    public boolean hasAttackBound(){return attackSource!=null;}
    public AttackMetadata getAttack(){return getAttackSource();}
    public double getModifier(String key){return getParameter(key);}
    @Override public String toString(){return "SkillMetadata{"+(cast==null?"none":cast)+",caster="+caster.getData().getUniqueId()+"}";}
}
