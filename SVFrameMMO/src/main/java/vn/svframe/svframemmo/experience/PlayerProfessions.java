package vn.svframe.svframemmo.experience;

import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerExperienceGainEvent;
import vn.svframe.svframemmo.api.event.PlayerLevelChangeEvent;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Persistent per-player profession levels/EXP with SVFrameMMO 1.13.1 overload semantics. */
public final class PlayerProfessions {
    private final Map<String, Double> experience = new LinkedHashMap<>();
    private final Map<String, Integer> levels = new LinkedHashMap<>();
    private final PlayerData playerData;

    public PlayerProfessions(PlayerData playerData) { this.playerData = Objects.requireNonNull(playerData, "playerData"); }
    public PlayerData getPlayerData() { return playerData; }

    public int getLevel(String profession) { return Math.max(1, levels.getOrDefault(profession, 1)); }
    public int getLevel(Profession profession) { return getLevel(profession.getId()); }
    public double getExperience(String profession) { return Math.max(0d, experience.getOrDefault(profession, 0d)); }
    public double getExperience(Profession profession) { return getExperience(profession.getId()); }
    public long getLevelUpExperience(Profession profession) { return profession.getExpCurve().getExperience(playerData, getLevel(profession)); }
    public boolean hasReachedMaxLevel(Profession profession) { return profession.hasMaxLevel() && getLevel(profession) >= profession.getMaxLevel(); }

    public void setLevel(Profession profession, int newLevel, PlayerLevelChangeEvent.Reason reason) {
        int target = Math.max(1, profession.hasMaxLevel() ? Math.min(profession.getMaxLevel(), newLevel) : newLevel);
        int old = getLevel(profession);
        levels.put(profession.getId(), target);
        if (old != target && reason != PlayerLevelChangeEvent.Reason.CHOOSE_PROFILE)
            new PlayerLevelChangeEvent(playerData, profession, old, target, reason).call();
    }

    public void setExperience(Profession profession, double value) { experience.put(profession.getId(), Math.max(0d, value)); }

    public void giveLevels(Profession profession, int amount, EXPSource source) {
        if (amount <= 0) { setLevel(profession, getLevel(profession) + amount, PlayerLevelChangeEvent.Reason.COMMAND); return; }
        long equivalent = 0;
        int current = getLevel(profession);
        while (amount-- > 0) equivalent += profession.getExpCurve().getExperience(playerData, current + amount);
        giveExperience(profession, equivalent, source);
    }

    public void giveExperience(Profession profession, double value, EXPSource source) {
        Objects.requireNonNull(profession, "profession");
        Objects.requireNonNull(source, "source");
        if (!playerData.isOnline()) throw new IllegalStateException("Cannot give profession experience to offline player");
        if (value <= 0d) {
            experience.merge(profession.getId(), value, (current, change) -> Math.max(0d, current + change));
            return;
        }
        if (hasReachedMaxLevel(profession)) { setExperience(profession, 0d); return; }

        double stat = playerData.getMMOPlayerData().getStatMap().getStat("ADDITIONAL_EXPERIENCE_" + UtilityMethods.enumName(profession.getId()));
        value *= 1d + stat / 100d;
        value *= SVFrameMMO.boosters().multiplier(profession.getKey());

        PlayerExperienceGainEvent event = new PlayerExperienceGainEvent(playerData, profession, value, source).call();
        if (event.isCancelled()) return;

        double currentExp = Math.max(0d, getExperience(profession) + event.getExperience());
        int oldLevel = getLevel(profession);
        int newLevel = oldLevel;
        long required = profession.getExpCurve().getExperience(playerData, newLevel);
        if (required <= 0) throw new IllegalStateException("Profession '" + profession.getId() + "' experience curve returned " + required + " at level " + newLevel);

        while (currentExp >= required) {
            if (profession.hasMaxLevel() && newLevel >= profession.getMaxLevel()) { currentExp = 0d; break; }
            currentExp -= required;
            newLevel++;
            double mainReward = profession.getMainExperienceReward(newLevel, playerData);
            if (mainReward != 0d) playerData.giveExperience(mainReward, EXPSource.PROFESSION_TO_CLASS);
            if (profession.hasExperienceTable())
                SVFrameMMO.experienceTables().claim(profession.getExperienceTableId(), profession.getKey(), playerData, newLevel);
            required = profession.getExpCurve().getExperience(playerData, newLevel);
            if (required <= 0) throw new IllegalStateException("Profession '" + profession.getId() + "' experience curve returned " + required + " at level " + newLevel);
        }

        experience.put(profession.getId(), currentExp);
        if (newLevel > oldLevel) setLevel(profession, newLevel, PlayerLevelChangeEvent.Reason.LEVEL_UP);
    }

    public Map<String, Integer> levelMap() { return Map.copyOf(levels); }
    public Map<String, Double> experienceMap() { return Map.copyOf(experience); }

    public void restore(Map<String, ? extends Number> restoredLevels, Map<String, ? extends Number> restoredExperience) {
        levels.clear(); experience.clear();
        if (restoredLevels != null) restoredLevels.forEach((id, value) -> {
            Profession profession = SVFrameMMO.professions().get(id);
            if (profession != null && value != null) levels.put(profession.getId(), Math.max(1, value.intValue()));
        });
        if (restoredExperience != null) restoredExperience.forEach((id, value) -> {
            Profession profession = SVFrameMMO.professions().get(id);
            if (profession != null && value != null) experience.put(profession.getId(), Math.max(0d, value.doubleValue()));
        });
    }
}
