package vn.svframe.svframemmo.api.player.profess.resource;

import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.ClassOption;

public enum PlayerResource {
    HEALTH("HEALTH_REGENERATION", "MAX_HEALTH_REGENERATION", "MAX_HEALTH", ClassOption.OFF_COMBAT_HEALTH_REGEN),
    MANA("MANA_REGENERATION", "MAX_MANA_REGENERATION", "MAX_MANA", ClassOption.OFF_COMBAT_MANA_REGEN),
    STAMINA("STAMINA_REGENERATION", "MAX_STAMINA_REGENERATION", "MAX_STAMINA", ClassOption.OFF_COMBAT_STAMINA_REGEN),
    STELLIUM("STELLIUM_REGENERATION", "MAX_STELLIUM_REGENERATION", "MAX_STELLIUM", ClassOption.OFF_COMBAT_STELLIUM_REGEN);

    private final String regenStat;
    private final String maxRegenStat;
    private final String maxStat;
    private final ClassOption offCombatRegen;

    PlayerResource(String regenStat, String maxRegenStat, String maxStat, ClassOption offCombatRegen) {
        this.regenStat = regenStat;
        this.maxRegenStat = maxRegenStat;
        this.maxStat = maxStat;
        this.offCombatRegen = offCombatRegen;
    }

    public String getRegenStat() { return regenStat; }
    public String getMaxRegenStat() { return maxRegenStat; }
    public String getMaxStat() { return maxStat; }
    public ClassOption getOffCombatRegen() { return offCombatRegen; }
    public double getCurrent(PlayerData data) { return data.getResource(this); }
    public double getMax(PlayerData data) { return data.getMaxResource(this); }
    public boolean setCurrent(PlayerData data, double amount, ResourceUpdateReason reason) { return data.setResource(this, amount, reason); }
    public boolean give(PlayerData data, double amount, ResourceUpdateReason reason) { return data.setResource(this, getCurrent(data) + amount, reason); }
    public boolean regen(PlayerData data, double amount) { return give(data, amount, ResourceUpdateReason.REGENERATION); }
}
