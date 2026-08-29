package vn.svframe.svframemmo.skill;

import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.gui.editable.placeholder.Placeholders;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.parameter.value.FormulaFailsafeException;
import vn.svframe.svframelib.skill.parameter.value.ScalingFormula;
import vn.svframe.svframelib.skill.trigger.TriggerType;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Skill metadata evaluated in a player-class context. */
public final class ClassSkill {
    private final SkillHandler<?> skill;
    private final int unlockLevel;
    private final int maxSkillLevel;
    private final TriggerType trigger;
    private final boolean unlockedByDefault;
    private final boolean permanent;
    private final boolean upgradable;
    private final Map<String, ScalingFormula> parameters = new LinkedHashMap<>();

    public ClassSkill(SkillHandler<?> skill, int unlockLevel, int maxSkillLevel, boolean unlockedByDefault, boolean needsBinding) {
        this(skill, unlockLevel, maxSkillLevel, unlockedByDefault, needsBinding, true);
    }

    public ClassSkill(SkillHandler<?> skill, int unlockLevel, int maxSkillLevel, boolean unlockedByDefault, boolean needsBinding, boolean upgradable) {
        this.skill = Objects.requireNonNull(skill, "skill");
        this.unlockLevel = unlockLevel;
        this.maxSkillLevel = maxSkillLevel;
        this.unlockedByDefault = unlockedByDefault;
        this.trigger = skill.getDefaultTriggerType();
        this.permanent = !needsBinding && trigger.isPassive();
        this.upgradable = upgradable;
        for (String parameter : skill.getParameters()) parameters.put(parameter, skill.getDefaultFormula(parameter));
    }

    public ClassSkill(SkillHandler<?> skill, Map<String, Object> config, boolean defaultNeedsBinding) {
        this.skill = Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(config, "config");
        this.unlockLevel = integer(config.get("level"), 0);
        this.maxSkillLevel = integer(config.get("max-level"), 0);
        this.unlockedByDefault = bool(config.get("unlocked-by-default"), true);
        this.trigger = config.containsKey("trigger")
                ? TriggerType.valueOf(UtilityMethods.enumName(String.valueOf(config.get("trigger"))))
                : skill.getDefaultTriggerType();
        this.permanent = !bool(config.get("needs-bound"), defaultNeedsBinding) && trigger.isPassive();
        this.upgradable = bool(config.get("upgradable"), true);
        for (String parameter : skill.getParameters()) {
            ScalingFormula fallback = skill.getDefaultFormula(parameter);
            Object configured = config.get(parameter);
            parameters.put(parameter, configured == null ? fallback : ScalingFormula.fromConfig(configured, fallback));
        }
    }

    public SkillHandler<?> getSkill() { return skill; }
    public TriggerType getTrigger() { return trigger; }
    public int getUnlockLevel() { return unlockLevel; }
    public boolean hasMaxLevel() { return maxSkillLevel > 0; }
    public int getMaxLevel() { return maxSkillLevel; }
    public boolean isUpgradable() { return upgradable; }
    public boolean isUnlockedByDefault() { return unlockedByDefault; }
    public boolean isPermanent() { return permanent; }
    public String getUnlockNamespacedKey() { return "skill:" + skill.getLowerCaseId(); }
    public String getCooldownPath() { return "skill_" + skill.getId(); }

    public ScalingFormula getParameterFormula(String parameter) {
        return Objects.requireNonNull(parameters.get(parameter), "Could not find parameter called '" + parameter + "'");
    }

    public void addParameter(String parameter, ScalingFormula formula) {
        if (!parameters.containsKey(parameter)) throw new IllegalArgumentException("Could not find parameter called '" + parameter + "'");
        parameters.put(parameter, Objects.requireNonNull(formula, "formula"));
    }

    public double getParameter(String parameter, int level, PlayerData caster) {
        try {
            return getParameterFormula(parameter).evaluate(level, caster == null ? null : caster.getPlayer());
        } catch (FormulaFailsafeException exception) {
            exception.log("Could not evaluate parameter '%s' of skill '%s'", parameter, skill.getId());
            return exception.getFailsafe();
        }
    }

    public double getParameter(String parameter, PlayerData caster) { return getParameter(parameter, caster.getSkillLevel(skill), caster); }
    public Map<String, ScalingFormula> getParameters() { return Map.copyOf(parameters); }

    /** MMOCore-style evaluated GUI lore, including class and item skill modifiers. */
    public List<String> calculateLore(PlayerData data) { return calculateLore(data, data.getSkillLevel(skill)); }

    public List<String> calculateLore(PlayerData data, int skillLevel) {
        Placeholders placeholders = new Placeholders();
        for (String parameter : parameters.keySet()) {
            double baseValue = getParameter(parameter, skillLevel, data);
            double modifiedValue = data.getMMOPlayerData().getSkillModifierMap().calculateValue(skill, baseValue, parameter);
            placeholders.register(parameter, skill.getParameterDecimalFormat(parameter).format(modifiedValue));
        }
        placeholders.register("level", skillLevel);
        placeholders.register("skill", skill.getName());
        List<String> result = new ArrayList<>();
        for (String line : skill.getUiLore()) result.add(placeholders.apply(data.getPlayer(), line));
        return List.copyOf(result);
    }

    private static int integer(Object value, int fallback) {
        try { return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
