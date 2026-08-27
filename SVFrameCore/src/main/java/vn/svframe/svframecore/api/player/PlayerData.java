package vn.svframe.svframecore.api.player;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframecore.SVFrameCore;
import vn.svframe.svframecore.api.event.PlayerResourceUpdateEvent;
import vn.svframe.svframecore.api.player.resource.PlayerResource;
import vn.svframe.svframecore.fabric.SVFrameCoreFabricMod;
import vn.svframe.svframecore.player.CombatHandler;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.player.resource.Resources;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native player state root. This is the first direct Fabric port of SVFrameCore PlayerData. */
public final class PlayerData {
    public static final String EXTERNAL_DATA_KEY = "svframecore";
    private static final ConcurrentHashMap<UUID, PlayerData> LOADED = new ConcurrentHashMap<>();

    private final MMOPlayerData mmoData;
    private final CombatHandler combat = new CombatHandler();
    private int level = 1;
    private double experience;
    private int classPoints;
    private int skillPoints;
    private int attributePoints;
    private int attributeReallocationPoints;
    private int skillTreeReallocationPoints;
    private int skillReallocationPoints;
    private double mana;
    private double stamina;
    private double stellium;
    private String playerClass = "";

    private PlayerData(MMOPlayerData mmoData) { this.mmoData = Objects.requireNonNull(mmoData, "MMO player data cannot be null"); }

    public static PlayerData getOrCreate(ServerPlayerEntity player) {
        MMOPlayerData mmo = MMOPlayerData.has(player) ? MMOPlayerData.get(player) : MMOPlayerData.setup(player);
        mmo.updatePlayer(player);
        return getOrCreate(mmo);
    }

    public static PlayerData getOrCreate(MMOPlayerData mmo) {
        PlayerData data = LOADED.computeIfAbsent(mmo.getUniqueId(), id -> new PlayerData(mmo));
        if (!mmo.hasExternalData(EXTERNAL_DATA_KEY)) mmo.setExternalData(EXTERNAL_DATA_KEY, data);
        return data;
    }

    public static PlayerData get(UUID id) { return Objects.requireNonNull(LOADED.get(id), "SVFrameCore player data not loaded"); }
    public static PlayerData get(MMOPlayerData data) { return getOrCreate(data); }
    public static PlayerData getOrNull(UUID id) { return LOADED.get(id); }
    public static Collection<PlayerData> getAll() { return java.util.List.copyOf(LOADED.values()); }
    public static void unload(UUID id) { PlayerData data = LOADED.remove(id); if (data != null) data.combat.clear(); }

    public MMOPlayerData getMMOPlayerData() { return mmoData; }
    public ServerPlayerEntity getPlayer() { return mmoData.getPlayer(); }
    public UUID getUniqueId() { return mmoData.getUniqueId(); }
    public boolean isOnline() { return mmoData.isOnline(); }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, level); }
    public double getExperience() { return experience; }
    public void setExperience(double experience) { this.experience = Math.max(0d, experience); }
    public String getPlayerClass() { return playerClass; }
    public void setPlayerClass(String playerClass) { this.playerClass = Objects.requireNonNullElse(playerClass, ""); }
    public CombatHandler getCombat() { return combat; }
    public boolean isInCombat() { return combat.isInCombat(SVFrameCoreFabricMod.currentTick()); }
    public void updateCombat() { combat.update(SVFrameCoreFabricMod.currentTick(), SVFrameCore.config().combatLogTicks()); }

    public int getClassPoints() { return classPoints; }
    public int getSkillPoints() { return skillPoints; }
    public int getAttributePoints() { return attributePoints; }
    public int getAttributeReallocationPoints() { return attributeReallocationPoints; }
    public int getSkillTreeReallocationPoints() { return skillTreeReallocationPoints; }
    public int getSkillReallocationPoints() { return skillReallocationPoints; }

    public double getMana() { return mana; }
    public double getStamina() { return stamina; }
    public double getStellium() { return stellium; }

    public boolean setHealth(double amount, ResourceUpdateReason reason) { return Resources.setHealth(getPlayer(), amount, reason); }
    public boolean giveHealth(double amount, ResourceUpdateReason reason) { return Resources.heal(getPlayer(), amount, reason); }
    public boolean setMana(double amount, ResourceUpdateReason reason) { return setResource(PlayerResource.MANA, amount, reason); }
    public boolean setStamina(double amount, ResourceUpdateReason reason) { return setResource(PlayerResource.STAMINA, amount, reason); }
    public boolean setStellium(double amount, ResourceUpdateReason reason) { return setResource(PlayerResource.STELLIUM, amount, reason); }
    public boolean giveMana(double amount, ResourceUpdateReason reason) { return setMana(mana + amount, reason); }
    public boolean giveStamina(double amount, ResourceUpdateReason reason) { return setStamina(stamina + amount, reason); }
    public boolean giveStellium(double amount, ResourceUpdateReason reason) { return setStellium(stellium + amount, reason); }

    private boolean setResource(PlayerResource resource, double requested, ResourceUpdateReason reason) {
        Objects.requireNonNull(reason, "Update reason cannot be null");
        double max = resource.getMax(this);
        double old = resource.rawCurrent(this);
        double next = Math.max(0d, Math.min(requested, max));
        if (Double.compare(old, next) == 0) return false;
        if (reason != ResourceUpdateReason.CHOOSE_CLASS) {
            PlayerResourceUpdateEvent event = new PlayerResourceUpdateEvent(this, resource, old, next, reason).call();
            if (event.isCancelled()) return false;
            next = Math.max(0d, Math.min(event.getNewAmount(), max));
        }
        resource.rawSet(this, next);
        return true;
    }

    public double maxStat(String id) { return Math.max(0d, mmoData.getStatMap().getStat(id)); }
    public void rawMana(double value) { mana = value; }
    public void rawStamina(double value) { stamina = value; }
    public void rawStellium(double value) { stellium = value; }
}
