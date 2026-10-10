package vn.svframe.svframelib.skill.handler;

import vn.svframe.svframelib.fabric.ReferenceClassSkillRuntime;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.result.def.SimpleSkillResult;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit adapter for the fourteen reference class skills. */
public final class ReferenceClassSkillHandler extends SkillHandler<SimpleSkillResult> {
    private final String behavior;
    private final vn.svframe.svframelib.skill.trigger.TriggerType trigger;
    public ReferenceClassSkillHandler(ConfigObject config,String behavior) {
        super(config);this.behavior=behavior;
        trigger=config.contains("trigger")?super.getDefaultTriggerType():
                vn.svframe.svframelib.skill.trigger.TriggerType.valueOf(config.getString("passive-type","CAST"));
        if(!ReferenceClassSkillRuntime.supports(behavior))throw new IllegalArgumentException("Unsupported native class skill: "+behavior);
    }
    @Override public vn.svframe.svframelib.skill.trigger.TriggerType getDefaultTriggerType(){return trigger;}
    @Override public java.util.List<String> getCategories(){
        var categories=new java.util.ArrayList<>(super.getCategories());categories.remove("ACTIVE");categories.remove("PASSIVE");
        categories.add(trigger.isPassive()?"PASSIVE":"ACTIVE");return java.util.List.copyOf(categories);
    }
    @Override public SimpleSkillResult getResult(SkillMetadata metadata) {
        return new SimpleSkillResult(metadata!=null&&metadata.getCaster()!=null&&ReferenceClassSkillRuntime.canCast(behavior,metadata.getCaster().getPlayer()));
    }
    @Override public void whenCast(SimpleSkillResult result,SkillMetadata metadata) {
        if(!result.isSuccessful(metadata))return;
        Map<String,Object> parameters=new LinkedHashMap<>();getParameters().forEach(key->parameters.put(key,metadata.getParameter(key)));
        ReferenceClassSkillRuntime.cast(behavior,metadata.getCaster().getPlayer(),parameters);
    }
}
