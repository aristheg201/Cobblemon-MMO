package vn.svframe.svframemmo.validation;

import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframemmo.api.player.attribute.PlayerAttribute;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NativeCoreSmoke {
    public static void main(String[] args) {
        if (PlayerResource.values().length != 4) throw new AssertionError("resource count");
        if (!PlayerResource.MANA.getMaxStat().equals("MAX_MANA")) throw new AssertionError("mana stat");

        Map<String, Object> buffs = new LinkedHashMap<>();
        buffs.put("weapon_damage", 2);
        buffs.put("max_health", "1%");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("name", "Strength");
        config.put("max-points", 40);
        config.put("buff", buffs);
        PlayerAttribute strength = new PlayerAttribute("STRENGTH", config);
        if (!strength.getId().equals("strength")) throw new AssertionError("attribute id normalization");
        if (strength.getMax() != 40 || strength.getBuffs().size() != 2) throw new AssertionError("attribute parsing");
        if (strength.getBuffs().get(0).type() != ModifierType.FLAT) throw new AssertionError("flat buff");
        if (strength.getBuffs().get(1).type() != ModifierType.RELATIVE) throw new AssertionError("relative buff");

        System.out.println("SVFRAMEMMO_NATIVE_CORE=PASS");
    }
}
