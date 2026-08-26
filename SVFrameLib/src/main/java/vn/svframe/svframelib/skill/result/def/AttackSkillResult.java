package vn.svframe.svframelib.skill.result.def;

import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.skill.result.SkillResult;

public class AttackSkillResult implements SkillResult {
    private final AttackMetadata attack;
    public AttackSkillResult(AttackMetadata attack) { this.attack = attack; }
    public AttackMetadata getAttack() { return attack; }
    public boolean isSuccessful() { return attack != null; }
}
