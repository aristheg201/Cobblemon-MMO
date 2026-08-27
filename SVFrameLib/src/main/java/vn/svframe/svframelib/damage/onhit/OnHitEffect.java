package vn.svframe.svframelib.damage.onhit;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.player.cooldown.CooldownObject;
import vn.svframe.svframelib.script.util.expression.numeric.NumericExpression;
import vn.svframe.svframelib.skill.SimpleSkill;
import vn.svframe.svframelib.skill.Skill;
import vn.svframe.svframelib.util.PostLoadAction;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.Objects;

/** Source-derived Fabric port of the 1.7.1 on-hit effect definition. */
public final class OnHitEffect implements CooldownObject {
    private final String id, cooldownPath;
    private final boolean skipEvent;
    private final Skill onAttack;
    private final Skill preAttack;
    private final NumericExpression cooldownFormula, rollFormula;
    private final PostLoadAction postLoadAction = new PostLoadAction(config -> { });

    public OnHitEffect(ConfigObject config) {
        Objects.requireNonNull(config, "config");
        if (!config.hasKey()) throw new IllegalArgumentException("On-hit effect requires a configuration key");
        this.id = config.getKey();
        this.cooldownPath = "mitigation:" + id;
        this.skipEvent = config.getBoolean("skip_event", false);
        this.cooldownFormula = config.contains("cooldown") ? NumericExpression.compile(config.getString("cooldown")) : null;
        this.rollFormula = config.contains("roll") ? NumericExpression.compile(config.getString("roll")) : null;
        if (!config.contains("on_attack")) throw new IllegalArgumentException("Missing on_attack for on-hit effect '" + id + "'");
        this.onAttack = new SimpleSkill(SVFrameLib.plugin.getSkills().loadSkillHandler(config.get("on_attack")));
        this.preAttack = config.contains("pre_attack") ? new SimpleSkill(SVFrameLib.plugin.getSkills().loadSkillHandler(config.get("pre_attack"))) : null;
    }

    public PostLoadAction getPostLoadAction() { return postLoadAction; }
    public NumericExpression getCooldown() { return cooldownFormula; }
    public boolean hasCooldown() { return cooldownFormula != null; }
    public boolean skipsEvent() { return skipEvent; }
    public NumericExpression getRoll() { return rollFormula; }
    public Skill onAttack() { return onAttack; }
    public Skill preAttack() { return preAttack; }
    public String getId() { return id; }
    @Override public String getCooldownPath() { return cooldownPath; }
}
