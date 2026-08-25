package io.lumine.mythic.lib.skill.result.def;

import io.lumine.mythic.lib.damage.AttackMetadata;
import io.lumine.mythic.lib.skill.result.SkillResult;

public class AttackSkillResult implements SkillResult {
    private final AttackMetadata attack;
    public AttackSkillResult(AttackMetadata attack) { this.attack = attack; }
    public AttackMetadata getAttack() { return attack; }
    public boolean isSuccessful() { return attack != null; }
}
