package vn.svframe.svframemmo.persistence;

import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.SavedClassState;

import java.util.Map;
import java.util.Set;

/** Immutable storage DTO shared by every native userdata backend. */
public record PlayerDataSnapshot(
        String playerClass, int level, double experience,
        int classPoints, int skillPoints, int attributePoints,
        int attributeReallocationPoints, int skillReallocationPoints, int skillTreeReallocationPoints,
        double health, double mana, double stamina, double stellium,
        Map<String, Integer> attributes,
        Map<String, Integer> skills,
        Map<Integer, String> bindings,
        Set<String> unlockedItems,
        Map<String, Integer> claims,
        Map<String, Integer> professionLevels,
        Map<String, Double> professionExperience,
        Map<String, Integer> skillTreePoints,
        Map<String, Integer> skillTreeNodeLevels,
        Map<String, SavedClassState> classSlots) {

    public static PlayerDataSnapshot capture(PlayerData value) {
        return new PlayerDataSnapshot(
                value.getClassId(), value.getLevel(), value.getExperience(),
                value.getClassPoints(), value.getSkillPoints(), value.getAttributePoints(),
                value.getAttributeReallocationPoints(), value.getSkillReallocationPoints(), value.getSkillTreeReallocationPoints(),
                value.getHealth(), value.getMana(), value.getStamina(), value.getStellium(),
                value.getAttributes().mapPoints(), value.getSkillLevels(), value.getSkillBindings(),
                value.getUnlockedItems(), value.getClaimCounts(),
                value.getProfessions().levelMap(), value.getProfessions().experienceMap(),
                value.getSkillTrees().pointMap(), value.getSkillTrees().nodeLevelMap(), value.getClassSlots());
    }

    public void apply(PlayerData value) {
        double restoredHealth = health <= 0d ? SVFrameMMO.config().defaultHealth() : health;
        value.restore(playerClass, level, experience, classPoints, skillPoints, attributePoints,
                attributeReallocationPoints, skillReallocationPoints, skillTreeReallocationPoints,
                restoredHealth, mana, stamina, stellium, attributes, skills, bindings, unlockedItems, claims,
                professionLevels, professionExperience, skillTreePoints, skillTreeNodeLevels, classSlots);
    }
}
