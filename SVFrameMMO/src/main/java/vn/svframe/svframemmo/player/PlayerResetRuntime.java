package vn.svframe.svframemmo.player;

import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerLevelChangeEvent;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.experience.Profession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Native implementation of MMOCore's administrative reset semantics. */
public final class PlayerResetRuntime {
    private PlayerResetRuntime() { }

    /** Clears every saved class slot and restores the configured default class data while preserving professions. */
    public static void resetClasses(PlayerData data) {
        if (data == null) throw new IllegalArgumentException("data");
        var cfg = SVFrameMMO.config();

        // Remove temporary progression/passive registrations belonging to the old class before replacing its state.
        data.prepareReload();

        Map<String, Integer> preservedClaims = new LinkedHashMap<>();
        data.getClaimCounts().forEach((key, value) -> {
            if (!isClassScopedClaim(key)) preservedClaims.put(key, value);
        });

        data.restore(
                SVFrameMMO.classes().getDefaultClass().getId(),
                cfg.defaultLevel(), 0d,
                cfg.defaultClassPoints(), cfg.defaultSkillPoints(), cfg.defaultAttributePoints(),
                cfg.defaultReallocationPoints(), cfg.defaultReallocationPoints(), cfg.defaultReallocationPoints(),
                cfg.defaultHealth(), cfg.defaultMana(), cfg.defaultStamina(), cfg.defaultStellium(),
                Map.of(), Map.of(), Map.of(), Set.of(), preservedClaims,
                data.getProfessions().levelMap(), data.getProfessions().experienceMap(),
                Map.of(), Map.of(), Map.of());

        data.reloadDefinitions();
        // HEALTH reads the live entity value, so explicitly apply all configured default resources after rebuilding stats.
        data.setResource(PlayerResource.HEALTH, cfg.defaultHealth(), ResourceUpdateReason.CHOOSE_CLASS);
        data.setResource(PlayerResource.MANA, cfg.defaultMana(), ResourceUpdateReason.CHOOSE_CLASS);
        data.setResource(PlayerResource.STAMINA, cfg.defaultStamina(), ResourceUpdateReason.CHOOSE_CLASS);
        data.setResource(PlayerResource.STELLIUM, cfg.defaultStellium(), ResourceUpdateReason.CHOOSE_CLASS);
    }

    /** Resets class and profession levels and removes their advancement claims/effects. */
    public static void resetLevels(PlayerData data) {
        var playerClass = data.getProfess();
        if (playerClass.hasExperienceTable())
            SVFrameMMO.experienceTables().unclaim(playerClass.getExperienceTableId(), playerClass.getKey(), data, true);
        data.setExperience(0d);
        data.setLevel(SVFrameMMO.config().defaultLevel(), PlayerLevelChangeEvent.Reason.RESET);

        for (Profession profession : SVFrameMMO.professions().getAll()) {
            if (profession.hasExperienceTable())
                SVFrameMMO.experienceTables().unclaim(profession.getExperienceTableId(), profession.getKey(), data, true);
            data.getProfessions().setExperience(profession, 0d);
            // Native profession state is one-based; this is the effective equivalent of the original setLevel(..., 0).
            data.getProfessions().setLevel(profession, 1, PlayerLevelChangeEvent.Reason.RESET);
        }
    }

    /** Resets skill levels/bindings and explicit skill/slot unlockables. */
    public static void resetSkills(PlayerData data) {
        data.resetSkills();
        for (String key : Set.copyOf(data.getUnlockedItems())) data.lock(key);
    }

    public static void resetAttributes(PlayerData data, boolean reallocate) {
        data.resetAttributes(reallocate);
    }

    public static void resetSkillTrees(PlayerData data) {
        data.getSkillTrees().resetAll(true);
    }

    /** Equivalent to the original reset-all for the subsystems in native SVFrameMMO scope. */
    public static void resetAll(PlayerData data, boolean reallocateAttributes) {
        resetClasses(data);
        resetLevels(data);
        resetSkills(data);
        resetAttributes(data, reallocateAttributes);
        resetSkillTrees(data);
        // Original reset-all clears the claim ledger after every subsystem-specific reset.
        for (String key : Set.copyOf(data.getClaimCounts().keySet())) data.setClaimCount(key, 0);
    }

    private static boolean isClassScopedClaim(String key) {
        return key != null && (key.startsWith("class_") || key.startsWith("attribute:") || key.startsWith("node:"));
    }
}
