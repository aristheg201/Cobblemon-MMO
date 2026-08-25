package io.lumine.mythic.lib.api.event.skill;

import io.lumine.mythic.lib.api.event.MMOPlayerDataEvent;
import io.lumine.mythic.lib.skill.Skill;
import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.skill.result.SkillResult;

/** Common Fabric-native base for MythicLib player skill events. */
public abstract class PlayerSkillEvent extends MMOPlayerDataEvent {
    private final SkillMetadata skillMeta;
    private final SkillResult result;
    private final Skill skill;

    protected PlayerSkillEvent(Skill skill, SkillMetadata skillMeta, SkillResult result) {
        super(skillMeta.getCaster().getData());
        this.skill = skill;
        this.skillMeta = skillMeta;
        this.result = result;
    }

    public Skill getCast() {
        return skill;
    }

    public SkillResult getResult() {
        return result;
    }

    public SkillMetadata getMetadata() {
        return skillMeta;
    }
}
