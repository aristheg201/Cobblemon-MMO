package io.lumine.mythic.lib.skill.handler;

import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.skill.result.def.SimpleSkillResult;
import io.lumine.mythic.lib.util.configobject.ConfigObject;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ScriptSkillHandler extends SkillHandler<SimpleSkillResult> {
    public ScriptSkillHandler(String id) { super(id); }

    public ScriptSkillHandler(String id, ConfigObject cfg) {
        super(id);
        if (cfg != null && cfg.contains("modifiers")) {
            String raw = cfg.getString("modifiers", "");
            for (String modifier : raw.split("[,;\s]+"))
                if (!modifier.isBlank()) registerModifiers(modifier.trim());
        }
    }

    @Override
    public SimpleSkillResult getResult(SkillMetadata metadata) {
        return new SimpleSkillResult(metadata != null && metadata.getCaster() != null);
    }

    @Override
    public void whenCast(SimpleSkillResult result, SkillMetadata metadata) {
        if (!result.isSuccessful()) return;
        Map<String, Object> params = new LinkedHashMap<>();
        for (String key : getParameters()) params.put(key, metadata.getParameter(key));
        var entity = metadata.getTargetEntityOrNull();
        UUID target = entity == null ? null : entity.getUuid();
        MythicLibFabricMod.castSkill(getId(), metadata.getCaster().getData().getUniqueId(), target, params);
    }
}
