package vn.svframe.svframemobs.mythic;

import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframelib.skill.trigger.TriggerType;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import java.util.*;

/** Prepares conditions once, before MMOCore commits costs; executes that exact plan. */
public final class MythicSkillHandler extends SkillHandler<MythicSkillHandler.Result> implements vn.svframe.svframelib.skill.NativeSkillProviders.BackedHandler {
    public record Result(MythicEngine.Prepared prepared) implements SkillResult {public boolean isSuccessful(){return prepared!=null;}}
    private final String skill;private final TriggerType trigger;
    public MythicSkillHandler(ConfigObject config,String skill){
        super(config);this.skill=skill;trigger=config.contains("trigger")?super.getDefaultTriggerType():TriggerType.valueOf(config.getString("passive-type","CAST"));
    }
    public String internalSkill(){return skill;}
    @Override public String providerNamespace(){return "mythicmobs";}
    @Override public String sourceSkill(){return skill;}
    @Override public TriggerType getDefaultTriggerType(){return trigger;}
    @Override public List<String> getCategories(){var result=new ArrayList<>(super.getCategories());result.remove("ACTIVE");result.remove("PASSIVE");result.add(trigger.isPassive()?"PASSIVE":"ACTIVE");String original=vn.svframe.svframelib.UtilityMethods.enumName(skill);if(!result.contains(original))result.add(original);return List.copyOf(result);}
    @Override public Result getResult(SkillMetadata metadata){
        if(metadata==null||metadata.getCaster()==null)return new Result(null);
        Map<String,Object> parameters=new LinkedHashMap<>();getParameters().forEach(key->parameters.put(key,metadata.getParameter(key)));
        var target=metadata.getTargetEntityOrNull();
        return new Result(MythicRuntime.instance().prepare(skill,metadata.getCaster().getData().getUniqueId(),target==null?null:target.getUuid(),parameters));
    }
    @Override public void whenCast(Result result,SkillMetadata metadata){if(result.isSuccessful())MythicRuntime.instance().start(result.prepared());}
}
