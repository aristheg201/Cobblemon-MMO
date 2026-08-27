package vn.svframe.svframelib.damage.mitigation;

import vn.svframe.svframelib.MythicLib;
import vn.svframe.svframelib.player.cooldown.CooldownObject;
import vn.svframe.svframelib.script.util.expression.numeric.NumericExpression;
import vn.svframe.svframelib.skill.SimpleSkill;
import vn.svframe.svframelib.skill.Skill;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.Locale;
import java.util.Objects;

/** Source-derived Fabric port of the 1.7.1 mitigation type definition. */
public final class MitigationType implements CooldownObject {
    private final String id, cooldownPath;
    private final boolean skipEvent;
    private final Skill onDamage;
    private final Skill preDamage;
    private final NumericExpression cooldownFormula, rollFormula;
    private final LegacyMitigationType legacy;

    public MitigationType(ConfigObject config) {
        Objects.requireNonNull(config, "config");
        if (!config.hasKey()) throw new IllegalArgumentException("Mitigation type requires a configuration key");
        this.id = config.getKey();
        this.cooldownPath = "mitigation:" + id;
        this.skipEvent = config.getBoolean("skip_event", false);
        this.legacy = config.contains("legacy") ? LegacyMitigationType.valueOf(config.getString("legacy").trim().toUpperCase(Locale.ROOT)) : null;
        this.cooldownFormula = config.contains("cooldown") ? NumericExpression.compile(config.getString("cooldown")) : null;
        this.rollFormula = config.contains("roll") ? NumericExpression.compile(config.getString("roll")) : null;
        if (!config.contains("on_damage")) throw new IllegalArgumentException("Missing on_damage for mitigation type '" + id + "'");
        this.onDamage = new SimpleSkill(MythicLib.plugin.getSkills().loadSkillHandler(config.get("on_damage")));
        this.preDamage = config.contains("pre_damage") ? new SimpleSkill(MythicLib.plugin.getSkills().loadSkillHandler(config.get("pre_damage"))) : null;
    }

    public NumericExpression getCooldown() { return cooldownFormula; }
    public boolean hasCooldown() { return cooldownFormula != null; }
    public boolean skipsEvent() { return skipEvent; }
    public NumericExpression getRoll() { return rollFormula; }
    public Skill onDamage() { return onDamage; }
    public Skill preDamage() { return preDamage; }
    public String getId() { return id; }
    public LegacyMitigationType asLegacy() { return legacy; }
    @Override public String getCooldownPath() { return cooldownPath; }
}
