package vn.svframe.svframelib.rpg.provided;

import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.rpg.ClassModule;
import vn.svframe.svframelib.rpg.LevelModule;
import vn.svframe.svframelib.rpg.ManaModule;

/**
 * SVFrameLib's fallback RPG provider, mapped to native player fields.
 * Level is vanilla experience level and mana is vanilla food level.
 */
public class DummyModule implements ClassModule, LevelModule, ManaModule {
    public static DummyModule INSTANCE = new DummyModule();

    @Override
    public String getClass(MMOPlayerData data) {
        return "";
    }

    @Override
    public int getLevel(MMOPlayerData data) {
        return data.getPlayer().experienceLevel;
    }

    @Override
    public boolean setMana(MMOPlayerData data, double value, ResourceUpdateReason reason) {
        data.getPlayer().getHungerManager().setFoodLevel((int) value);
        return true;
    }

    @Override
    public boolean setStamina(MMOPlayerData data, double value, ResourceUpdateReason reason) {
        return false;
    }

    @Override
    public double getMana(MMOPlayerData data) {
        return data.getPlayer().getHungerManager().getFoodLevel();
    }

    @Override
    public double getStamina(MMOPlayerData data) {
        return 0;
    }
}
