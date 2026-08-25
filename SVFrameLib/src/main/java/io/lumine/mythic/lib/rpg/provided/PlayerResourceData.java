package io.lumine.mythic.lib.rpg.provided;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.PlayerDataMap;
import io.lumine.mythic.lib.player.resource.ResourceUpdateReason;

/** Native player mana/stamina state with the 1.7.1 session initialization semantics. */
public class PlayerResourceData extends PlayerDataMap {
    private final MMOPlayerData parent;
    private boolean initialized;
    private double mana;
    private double stamina;

    public PlayerResourceData(MMOPlayerData parent) {
        this.parent = parent;
    }

    @Override
    protected void onSessionOpen() {
        if (!initialized) {
            initialized = true;
            mana = MythicLib.plugin.getMMOConfig().manaLoginRatio * parent.getStatMap().getStat("MAX_MANA");
            stamina = MythicLib.plugin.getMMOConfig().staminaLoginRatio * parent.getStatMap().getStat("MAX_STAMINA");
        }
    }

    public MMOPlayerData getParent() { return parent; }
    public double getMana() { return mana; }
    public double getStamina() { return stamina; }

    public boolean setMana(double amount, ResourceUpdateReason reason) {
        double max = parent.getStatMap().getStat("MAX_MANA");
        double next = Math.max(0d, Math.min(amount, max));
        if (mana == next) return true;
        if (reason != ResourceUpdateReason.CHOOSE_CLASS) {
            ResourceUpdateEvent event = new ResourceUpdateEvent(parent, mana, next, reason, PlayerResource.MANA).call();
            if (event.isCancelled()) return false;
            next = event.getNewAmount();
        }
        mana = Math.max(0d, Math.min(next, max));
        return true;
    }

    public boolean setStamina(double amount, ResourceUpdateReason reason) {
        double max = parent.getStatMap().getStat("MAX_STAMINA");
        double next = Math.max(0d, Math.min(amount, max));
        if (stamina == next) return true;
        if (reason != ResourceUpdateReason.CHOOSE_CLASS) {
            ResourceUpdateEvent event = new ResourceUpdateEvent(parent, stamina, next, reason, PlayerResource.STAMINA).call();
            if (event.isCancelled()) return false;
            next = event.getNewAmount();
        }
        stamina = Math.max(0d, Math.min(next, max));
        return false;
    }

    public void giveMana(double amount, ResourceUpdateReason reason) { setMana(mana + amount, reason); }
    public void giveStamina(double amount, ResourceUpdateReason reason) { setStamina(stamina + amount, reason); }
}
