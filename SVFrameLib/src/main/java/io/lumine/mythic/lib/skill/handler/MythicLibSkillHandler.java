package io.lumine.mythic.lib.skill.handler;

import io.lumine.mythic.lib.script.MechanicQueue;
import io.lumine.mythic.lib.script.Script;
import io.lumine.mythic.lib.script.condition.Condition;
import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.skill.result.def.SimpleSkillResult;
import io.lumine.mythic.lib.util.configobject.ConfigObject;

import java.util.Objects;

public class MythicLibSkillHandler extends ScriptSkillHandler {
    private final Script script;

    public MythicLibSkillHandler(String id) { super(id); this.script = null; }
    public MythicLibSkillHandler(ConfigObject cfg) { super("MythicLibSkillHandler", cfg); this.script = null; }
    public MythicLibSkillHandler(Script script) {
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
