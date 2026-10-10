package vn.svframe.svframelib.skill.handler;

import vn.svframe.svframelib.fabric.HeroEntranceRuntime;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.result.def.SimpleSkillResult;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native extension; the upstream set of 90 default handlers stays intact. */
public final class HeroEntranceSkillHandler extends SkillHandler<SimpleSkillResult> {
    public HeroEntranceSkillHandler(ConfigObject config) { super("HERO_ENTRANCE", config); }
    @Override public SimpleSkillResult getResult(SkillMetadata metadata) {
        return new SimpleSkillResult(metadata != null && metadata.getCaster() != null
                && HeroEntranceRuntime.canStart(metadata.getCaster().getPlayer()));
    }
    @Override public void whenCast(SimpleSkillResult result, SkillMetadata metadata) {
        if (!result.isSuccessful(metadata)) return;
        Map<String, Object> parameters = new LinkedHashMap<>();
        getParameters().forEach(key -> parameters.put(key, metadata.getParameter(key)));
        HeroEntranceRuntime.start(metadata.getCaster().getPlayer(), parameters);
    }
}
