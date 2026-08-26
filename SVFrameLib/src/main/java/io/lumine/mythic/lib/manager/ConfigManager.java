package io.lumine.mythic.lib.manager;

import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.module.Module;
import io.lumine.mythic.lib.damage.DamageType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/** Native configuration state consumed by the 1.7.1 compatibility API. */
public class ConfigManager extends Module {
    public final DecimalFormatSymbols formatSymbols = DecimalFormatSymbols.getInstance(Locale.US);
    public DecimalFormat decimal = newDecimalFormat("0.#");
    public DecimalFormat decimals = newDecimalFormat("0.##");
    public boolean playerAbilityDamage = true;
    public boolean castingDelayCancelOnMove = true;
    public boolean enableCastingDelayBossbar = true;
    public boolean fixTooLargePackets = true;
    public boolean debugMode;
    public boolean ignoreShiftTriggers;
    public boolean ignoreOffhandClickTriggers;
    public boolean skipElementalDamageApplication;
    public boolean flagCheckSkills = true;
    public String naturalDefenseFormula = "LOWER";
    public String elementalDefenseFormula = "LOWER";
    public String castingDelayBossbarFormat = "<skill_name>";
    public double castingDelaySlowness;
    public int maxSyncTries = 10;
    public List<DamageType> meleeWeaponAttackTypes = List.of(DamageType.WEAPON, DamageType.PHYSICAL);
    public List<DamageType> meleeUnarmedAttackTypes = List.of(DamageType.PHYSICAL);
    public List<DamageType> meleeRandomAttackTypes = List.of(DamageType.PHYSICAL);
    public List<DamageType> bowAttackTypes = List.of(DamageType.WEAPON, DamageType.PHYSICAL, DamageType.PROJECTILE);
    public List<DamageType> skillAttackTypes = List.of(DamageType.SKILL);
    public int manaRefreshRate = 10;
    public double manaLoginRatio = 1d;
    public double staminaLoginRatio = 1d;

    public ConfigManager(MMOPlugin plugin) { super(plugin, "config"); }

    public DecimalFormat newDecimalFormat(String pattern) {
        DecimalFormat format = new DecimalFormat(pattern == null || pattern.isBlank() ? "0.#" : pattern, formatSymbols);
        format.setGroupingUsed(false);
        return format;
    }
}
