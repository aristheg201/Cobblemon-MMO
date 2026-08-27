package vn.svframe.svframemmo.validation;

import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframemmo.api.player.attribute.PlayerAttribute;
import vn.svframe.svframemmo.api.player.profess.ClassOption;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.experience.curve.FormulaExperienceCurve;
import vn.svframe.svframemmo.experience.curve.ListExperienceCurve;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java validation for data/model code which does not require an initialized
 * Fabric Loader or Minecraft server. Runtime registry invariants are validated
 * by the dedicated Fabric server boot step instead.
 */
public final class NativeCoreSmoke {
    public static void main(String[] args) {
        if (PlayerResource.values().length != 4) throw new AssertionError("resource count");
        if (!PlayerResource.MANA.getMaxStat().equals("MAX_MANA")) throw new AssertionError("mana stat");
        if (PlayerResource.MANA.getOffCombatRegen() != ClassOption.OFF_COMBAT_MANA_REGEN) throw new AssertionError("mana class option");

        Map<String, Object> buffs = new LinkedHashMap<>();
        buffs.put("weapon_damage", 2);
        buffs.put("max_health", "1%");
        Map<String, Object> attributeConfig = new LinkedHashMap<>();
        attributeConfig.put("name", "Strength");
        attributeConfig.put("max-points", 40);
        attributeConfig.put("buff", buffs);
        PlayerAttribute strength = new PlayerAttribute("STRENGTH", attributeConfig);
        if (!strength.getId().equals("strength")) throw new AssertionError("attribute id normalization");
        if (strength.getMax() != 40 || strength.getBuffs().size() != 2) throw new AssertionError("attribute parsing");
        if (strength.getBuffs().get(0).type() != ModifierType.FLAT) throw new AssertionError("flat buff");
        if (strength.getBuffs().get(1).type() != ModifierType.RELATIVE) throw new AssertionError("relative buff");

        ListExperienceCurve table = new ListExperienceCurve(List.of(100L, 250L, 500L));
        if (table.getExperience(null, 1) != 100L) throw new AssertionError("list curve level 1");
        if (table.getExperience(null, 3) != 500L) throw new AssertionError("list curve level 3");
        if (table.getExperience(null, 99) != 500L) throw new AssertionError("list curve max clamp");

        FormulaExperienceCurve formula = new FormulaExperienceCurve("{level} * 100");
        if (formula.getExperience(null, 4) != 400L) throw new AssertionError("formula curve");

        System.out.println("SVFRAMEMMO_NATIVE_CORE=PASS");
    }
}
