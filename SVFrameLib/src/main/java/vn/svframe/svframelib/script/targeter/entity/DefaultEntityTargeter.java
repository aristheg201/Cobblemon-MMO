package vn.svframe.svframelib.script.targeter.entity;

import net.minecraft.entity.Entity;
import vn.svframe.svframelib.script.targeter.EntityTargeter;
import vn.svframe.svframelib.skill.SkillMetadata;

import java.util.List;
import java.util.UUID;

public class DefaultEntityTargeter implements EntityTargeter {
    @Override public List<UUID> findTargets(SkillMetadata meta) { Entity e = meta.getSkillEntity(false); return List.of(e.getUuid()); }
}
