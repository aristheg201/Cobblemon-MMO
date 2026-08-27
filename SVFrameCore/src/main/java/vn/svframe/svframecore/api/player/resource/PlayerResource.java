package vn.svframe.svframecore.api.player.resource;

import vn.svframe.svframecore.api.player.PlayerData;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;

import java.util.Objects;

/** Four-resource model preserved from SVFrameCore 1.13.1. */
public enum PlayerResource {
    HEALTH("HEALTH_REGENERATION", "MAX_HEALTH_REGENERATION", "MAX_HEALTH"),
    MANA("MANA_REGENERATION", "MAX_MANA_REGENERATION", "MAX_MANA"),
    STAMINA("STAMINA_REGENERATION", "MAX_STAMINA_REGENERATION", "MAX_STAMINA"),
    STELLIUM("STELLIUM_REGENERATION", "MAX_STELLIUM_REGENERATION", "MAX_STELLIUM");

    private final String regenStat;
    private final String maxRegenStat;
    private final String maxStat;

    PlayerResource(String regenStat, String maxRegenStat, String maxStat) {
        this.regenStat = regenStat;
        this.maxRegenStat = maxRegenStat;
        this.maxStat = maxStat;
    }

    public String getRegenStat() { return regenStat; }
    public String getMaxRegenStat() { return maxRegenStat; }
    public String getMaxStat() { return maxStat; }

    public double getCurrent(PlayerData data) {
        Objects.requireNonNull(data, "Player data cannot be null");
        return switch (this) {
            case HEALTH -> data.getPlayer().getHealth();
            case MANA -> data.getMana();
            case STAMINA -> data.getStamina();
            case STELLIUM -> data.getStellium();
        };
    }

    public double getMax(PlayerData data) {
        Objects.requireNonNull(data, "Player data cannot be null");
        return this == HEALTH ? data.getPlayer().getMaxHealth() : data.maxStat(maxStat);
    }

    public void regen(PlayerData data, double amount) { give(data, amount, ResourceUpdateReason.REGENERATION); }
    public boolean setCurrent(PlayerData data, double amount, ResourceUpdateReason reason) {
        return switch (this) {
            case HEALTH -> data.setHealth(amount, reason);
            case MANA -> data.setMana(amount, reason);
            case STAMINA -> data.setStamina(amount, reason);
            case STELLIUM -> data.setStellium(amount, reason);
        };
    }
    public boolean give(PlayerData data, double amount, ResourceUpdateReason reason) {
        return switch (this) {
            case HEALTH -> data.giveHealth(amount, reason);
            case MANA -> data.giveMana(amount, reason);
            case STAMINA -> data.giveStamina(amount, reason);
            case STELLIUM -> data.giveStellium(amount, reason);
        };
    }

    public double rawCurrent(PlayerData data) {
        return switch (this) {
            case HEALTH -> data.getPlayer().getHealth();
            case MANA -> data.getMana();
            case STAMINA -> data.getStamina();
            case STELLIUM -> data.getStellium();
        };
    }
    public void rawSet(PlayerData data, double value) {
        switch (this) {
            case HEALTH -> data.getPlayer().setHealth((float) value);
            case MANA -> data.rawMana(value);
            case STAMINA -> data.rawStamina(value);
            case STELLIUM -> data.rawStellium(value);
        }
    }
}
