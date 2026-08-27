package vn.svframe.svframemmo.validation;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframemmo.api.player.attribute.PlayerAttribute;
import vn.svframe.svframemmo.api.player.profess.ClassOption;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.api.player.profess.resource.ResourceRegeneration;
import vn.svframe.svframemmo.manager.ClassManager;
import vn.svframe.svframemmo.skill.SVFrameMMOSkillBootstrap;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NativeCoreSmoke {
    private static final String[] CLASS_FILES = {
            "human.yml", "marksman.yml", "paladin.yml", "rogue.yml", "warrior.yml",
            "mage/mage.yml", "mage/arcane-mage.yml"
    };
    private static final String[] SKILL_FILES = {"ambers.yml", "neptune-gift.yml", "sneaky-picky.yml"};

    public static void main(String[] args) throws Exception {
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

        SVFrameLib lib = SVFrameLib.bootstrap();
        if (lib.getSkills().getHandlers().size() != 90) throw new AssertionError("built-in skill materialization");

        Path root = Files.createTempDirectory("svframemmo-native-smoke");
        copy("defaults/stats.yml", root.resolve("stats.yml"));
        for (String file : CLASS_FILES) copy("defaults/classes/" + file, root.resolve("classes").resolve(file));
        for (String file : SKILL_FILES) copy("defaults/skills/" + file, root.resolve("skills").resolve(file));

        SVFrameMMOSkillBootstrap.register(root.resolve("skills"));
        if (lib.getSkills().getHandlers().size() != 93) throw new AssertionError("combined skill registry");

        ClassManager classes = new ClassManager();
        classes.reload(root.resolve("classes"), root.resolve("stats.yml"), root.resolve("exp-curves"), true);
        if (classes.size() != 7) throw new AssertionError("class corpus count");
        if (!classes.getDefaultClass().getId().equals("HUMAN")) throw new AssertionError("default class");
        if (classes.getDefaultStats().get("MAX_MANA").evaluate(1, null) != 20d) throw new AssertionError("default stat formula");

        var mage = classes.getOrThrow("MAGE");
        if (mage.getSkill("FIRE_STORM") == null || mage.getSkill("AMBERS") == null) throw new AssertionError("class skill population");
        if (mage.getSkill("FIRE_STORM").getParameter("damage", 1, null) != 5d) throw new AssertionError("class skill parameter override");
        if (mage.getHandler(PlayerResource.MANA).getType() != ResourceRegeneration.HandlerType.MAX) throw new AssertionError("special mana regeneration");
        if (!mage.getHandler(PlayerResource.MANA).isOffCombatOnly()) throw new AssertionError("off-combat resource rule");
        if (mage.getSubclasses().size() != 1 || !mage.getSubclasses().getFirst().getProfess().getId().equals("ARCANE_MAGE")
                || mage.getSubclasses().getFirst().getLevel() != 10) throw new AssertionError("subclass resolution");
        if (mage.getExpCurve().getExperience(null, 2) != 400L) throw new AssertionError("class experience formula");
        if (mage.getSlots().size() != 4 || !mage.getSkillSlot(1).unlockedByDefault()) throw new AssertionError("skill slot parsing");

        System.out.println("SVFRAMEMMO_NATIVE_CORE=PASS");
    }

    private static void copy(String resource, Path output) throws Exception {
        Files.createDirectories(output.getParent());
        try (InputStream input = NativeCoreSmoke.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing smoke resource " + resource);
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
