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
    public ReferenceClassSkillHandler(ConfigObject config,String behavior) {
        super(config);this.behavior=behavior;
        if(!ReferenceClassSkillRuntime.supports(behavior))throw new IllegalArgumentException("Unsupported native class skill: "+behavior);
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
