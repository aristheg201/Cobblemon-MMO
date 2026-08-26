package vn.svframe.svframelib.script.targeter.entity;

import vn.svframe.svframelib.script.targeter.EntityTargeter;
import vn.svframe.svframelib.skill.SkillMetadata;

import java.util.List;
import java.util.UUID;

public class TargetTargeter implements EntityTargeter {
    @Override public List<UUID> findTargets(SkillMetadata meta) { return List.of(meta.getTargetEntity().getUuid()); }
}
