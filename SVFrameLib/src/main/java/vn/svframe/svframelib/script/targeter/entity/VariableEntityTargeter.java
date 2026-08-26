package vn.svframe.svframelib.script.targeter.entity;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.script.targeter.EntityTargeter;
import vn.svframe.svframelib.script.variable.Variable;
import vn.svframe.svframelib.script.variable.def.EntityVariable;
import vn.svframe.svframelib.script.variable.def.PlayerVariable;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.List;
import java.util.UUID;

public class VariableEntityTargeter implements EntityTargeter {
    private final String name;
    public VariableEntityTargeter(ConfigObject config) { config.validateKeys("name"); name = config.getString("name"); }
    @Override public List<UUID> findTargets(SkillMetadata meta) {
        Variable<?> var = meta.getVariable(name);
        if (var instanceof EntityVariable entityVar) { Entity e = entityVar.getStored(); if (e != null) return List.of(e.getUuid()); }
        if (var instanceof PlayerVariable playerVar) { ServerPlayerEntity p = playerVar.getStored(); if (p != null) return List.of(p.getUuid()); }
        throw new IllegalArgumentException("Variable '" + name + "' is not an entity");
    }
}
