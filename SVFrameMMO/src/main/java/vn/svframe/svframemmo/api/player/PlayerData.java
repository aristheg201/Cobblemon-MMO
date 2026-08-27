package vn.svframe.svframemmo.api.player;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.fabric.runtime.RpgProfileRegistry;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerResourceUpdateEvent;
import vn.svframe.svframemmo.api.player.attribute.PlayerAttributes;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Native persistent player state for SVFrameMMO. */
public final class PlayerData {
    private final UUID id;
    private transient ServerPlayerEntity player;
    private String playerClass = "HUMAN";
    private int level, classPoints, skillPoints, attributePoints;
    private double experience, mana, stamina, stellium;
    private long combatUntilTick;
    private final PlayerAttributes attributes = new PlayerAttributes(this);
    private final Map<String, Integer> skillLevels = new LinkedHashMap<>();

    PlayerData(UUID id) {
        this.id = id;
        var config = SVFrameMMO.config();
        level = config.defaultLevel();
        classPoints = config.defaultClassPoints();
        skillPoints = config.defaultSkillPoints();
        attributePoints = config.defaultAttributePoints();
        mana = config.defaultMana();
        stamina = config.defaultStamina();
        stellium = config.defaultStellium();
    }

    public UUID getUniqueId() { return id; }
    public ServerPlayerEntity getPlayer() { return player; }
    public MMOPlayerData getMMOPlayerData() { return player == null ? MMOPlayerData.get(id) : MMOPlayerData.setup(player); }

    public void attach(ServerPlayerEntity player) {
        this.player = player;
        MMOPlayerData.setup(player);
        attributes.reload();
        clampAll();
    }

    public void detach() { player = null; }

    public String getClassId() { return playerClass; }
    public void setClassId(String value) { playerClass = normalizeEnum(value == null ? "HUMAN" : value); }

    public int getLevel() { return level; }
    public void setLevel(int value) { level = Math.max(1, value); }
    public double getExperience() { return experience; }
    public void setExperience(double value) { experience = Math.max(0, value); }

    public int getClassPoints() { return classPoints; }
    public void setClassPoints(int value) { classPoints = Math.max(0, value); }
    public void giveClassPoints(int value) { setClassPoints(classPoints + value); }
    public int getSkillPoints() { return skillPoints; }
    public void setSkillPoints(int value) { skillPoints = Math.max(0, value); }
    public void giveSkillPoints(int value) { setSkillPoints(skillPoints + value); }
    public int getAttributePoints() { return attributePoints; }
    public void setAttributePoints(int value) { attributePoints = Math.max(0, value); }
    public void giveAttributePoints(int value) { setAttributePoints(attributePoints + value); }

    public PlayerAttributes getAttributes() { return attributes; }
    public Map<String, Integer> mapAttributeLevels() { return attributes.mapPoints(); }

    public int getSkillLevel(SkillHandler<?> skill) { return getSkillLevel(skill.getId()); }
    public int getSkillLevel(String skill) { return skillLevels.getOrDefault(normalizeEnum(skill), 1); }
    public void setSkillLevel(SkillHandler<?> skill, int value) { setSkillLevel(skill.getId(), value); }
    public void setSkillLevel(String skill, int value) {
        String skillId = normalizeEnum(skill);
        if (value <= 1) skillLevels.remove(skillId);
        else skillLevels.put(skillId, value);
    }
    public void resetSkills() { skillLevels.clear(); }
    public Map<String, Integer> getSkillLevels() { return Collections.unmodifiableMap(skillLevels); }

    public boolean isInCombat() { return SVFrameMMO.currentTick() < combatUntilTick; }
    public void markCombat() { combatUntilTick = SVFrameMMO.currentTick() + SVFrameMMO.config().combatTimerSeconds() * 20L; }

    public double getMana() { return mana; }
    public double getStamina() { return stamina; }
    public double getStellium() { return stellium; }
    public boolean setMana(double amount, ResourceUpdateReason reason) { return setResource(PlayerResource.MANA, amount, reason); }
    public boolean giveMana(double amount, ResourceUpdateReason reason) { return setMana(mana + amount, reason); }
    public boolean setStamina(double amount, ResourceUpdateReason reason) { return setResource(PlayerResource.STAMINA, amount, reason); }
    public boolean giveStamina(double amount, ResourceUpdateReason reason) { return setStamina(stamina + amount, reason); }
    public boolean setStellium(double amount, ResourceUpdateReason reason) { return setResource(PlayerResource.STELLIUM, amount, reason); }
    public boolean giveStellium(double amount, ResourceUpdateReason reason) { return setStellium(stellium + amount, reason); }

    public double getResource(PlayerResource resource) {
        return switch (resource) {
            case HEALTH -> player == null ? 0 : player.getHealth();
            case MANA -> mana;
            case STAMINA -> stamina;
            case STELLIUM -> stellium;
        };
    }

    public double getMaxResource(PlayerResource resource) {
        if (resource == PlayerResource.HEALTH) return player == null ? 20 : player.getMaxHealth();
        return Math.max(0, getMMOPlayerData().getStatMap().getStat(resource.getMaxStat()));
    }

    public boolean setResource(PlayerResource resource, double amount, ResourceUpdateReason reason) {
        double max = getMaxResource(resource);
        double old = getResource(resource);
        double next = Math.max(0, Math.min(amount, max));
        if (Double.compare(old, next) == 0) return false;
        if (reason != ResourceUpdateReason.CHOOSE_CLASS) {
            var event = new PlayerResourceUpdateEvent(this, resource, old, next, reason).call();
            if (event.isCancelled()) return false;
            next = Math.max(0, Math.min(event.getNewAmount(), max));
        }
        switch (resource) {
            case HEALTH -> {
                if (player == null) return false;
                player.setHealth((float) next);
            }
            case MANA -> mana = next;
            case STAMINA -> stamina = next;
            case STELLIUM -> stellium = next;
        }
        return true;
    }

    public void clampAll() {
        for (PlayerResource resource : PlayerResource.values()) {
            if (resource != PlayerResource.HEALTH) setResource(resource, getResource(resource), ResourceUpdateReason.CLAMPING);
        }
    }

    public RpgProfileRegistry.Snapshot snapshot() {
        Map<String, Double> mapped = new LinkedHashMap<>();
        attributes.mapPoints().forEach((key, value) -> mapped.put(key, value.doubleValue()));
        return new RpgProfileRegistry.Snapshot(level, playerClass, mapped);
    }

    public static PlayerData blank(UUID id) { return new PlayerData(id); }

    public void restore(String clazz, int level, double exp, int cp, int sp, int ap,
                        double mana, double stamina, double stellium,
                        Map<String, ? extends Number> attrs,
                        Map<String, ? extends Number> skills) {
        playerClass = clazz == null ? "HUMAN" : normalizeEnum(clazz);
        this.level = Math.max(1, level);
        experience = Math.max(0, exp);
        classPoints = Math.max(0, cp);
        skillPoints = Math.max(0, sp);
        attributePoints = Math.max(0, ap);
        this.mana = mana;
        this.stamina = stamina;
        this.stellium = stellium;
        attributes.load(attrs);
        skillLevels.clear();
        if (skills != null) {
            skills.forEach((key, value) -> {
                if (key != null && value != null) setSkillLevel(key, value.intValue());
            });
        }
    }

    private static String normalizeEnum(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
