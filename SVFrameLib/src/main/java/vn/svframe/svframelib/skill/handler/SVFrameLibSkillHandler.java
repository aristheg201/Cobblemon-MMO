package vn.svframe.svframelib.skill.handler;

import vn.svframe.svframelib.script.MechanicQueue;
import vn.svframe.svframelib.script.Script;
import vn.svframe.svframelib.script.condition.Condition;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.result.def.SimpleSkillResult;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.Objects;

public class SVFrameLibSkillHandler extends ScriptSkillHandler {
    private final Script script;

    public SVFrameLibSkillHandler(String id) { super(id); this.script = null; }
    public SVFrameLibSkillHandler(ConfigObject cfg) { super("SVFrameLibSkillHandler", cfg); this.script = null; }
    public SVFrameLibSkillHandler(Script script) {
        super(Objects.requireNonNull(script, "script").getId());
        this.script = script;
    }

    @Override
    public SimpleSkillResult getResult(SkillMetadata metadata) {
        if (script == null) return super.getResult(metadata);
        if (metadata == null || metadata.getCaster() == null) return new SimpleSkillResult(false);
        for (Condition condition : script.getConditions())
            if (!condition.checkIfMet(metadata)) return new SimpleSkillResult(false);
        return new SimpleSkillResult(true);
    }

    @Override
    public void whenCast(SimpleSkillResult result, SkillMetadata metadata) {
        if (!result.isSuccessful()) return;
        if (script == null) { super.whenCast(result, metadata); return; }
        new MechanicQueue(metadata, script).next();
    }
}
