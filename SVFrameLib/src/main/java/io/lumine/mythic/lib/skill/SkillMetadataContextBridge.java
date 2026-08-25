package io.lumine.mythic.lib.skill;

import io.lumine.mythic.lib.skill.handler.SkillHandler;
import vn.svframe.mythiclibfabric.runtime.script.ScriptContext;

import java.util.UUID;

public final class SkillMetadataContextBridge {
    private SkillMetadataContextBridge() {}

    public static ScriptContext context(SkillMetadata metadata) {
        if (metadata == null || metadata.getCaster() == null)
            throw new IllegalArgumentException("Skill metadata/caster cannot be null");

        UUID caster = metadata.getCaster().getData().getUniqueId();
        var targetEntity = metadata.getTargetEntityOrNull();
        UUID target = targetEntity == null ? caster : targetEntity.getUuid();
        ScriptContext context = new ScriptContext(caster, target);

        if (metadata.getCast() != null) {
            SkillHandler<?> handler = metadata.getCast().getHandler();
            if (handler != null) for (String key : handler.getParameters()) {
                double value = metadata.getParameter(key);
                context.numbers().put(key, value);
                context.numbers().put("parameter." + key, value);
                context.numbers().put("modifier." + key, value);
                context.objects().put(key, value);
                context.objects().put("parameter." + key, value);
                context.objects().put("modifier." + key, value);
            }
        }

        if (metadata.hasAttackSource()) context.objects().put("attack", metadata.getAttackSource());
        context.objects().put("skill_metadata", metadata);
        return context;
    }
}
