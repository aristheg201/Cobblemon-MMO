package vn.svframe.svframelib.player;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.MythicLib;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.StatInstance;
import vn.svframe.svframelib.api.stat.StatMap;
import vn.svframe.svframelib.api.stat.provider.PlayerStatProvider;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.DamageMetadata;
import vn.svframe.svframelib.damage.DamageType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PlayerMetadata implements PlayerStatProvider {
    private final ServerPlayerEntity player;
    private final MMOPlayerData playerData;
    private final Map<String, Double> playerStats;
    private final EquipmentSlot actionHand;

    public PlayerMetadata(PlayerMetadata parent) {
        Objects.requireNonNull(parent, "Parent cannot be null");
        this.player = parent.player;
        this.playerData = parent.playerData;
        this.playerStats = parent.playerStats;
        this.actionHand = parent.actionHand;
    }

    public PlayerMetadata(MMOPlayerData playerData) {
        this.player = null;
        this.playerData = Objects.requireNonNull(playerData, "Player data cannot be null");
        this.playerStats = new HashMap<>();
        this.actionHand = EquipmentSlot.MAIN_HAND;
    }

    public PlayerMetadata(StatMap statMap, EquipmentSlot actionHand) {
        this.player = statMap.getData().getPlayer();
        this.playerData = statMap.getData();
        this.playerStats = new HashMap<>();
        this.actionHand = Objects.requireNonNull(actionHand, "Action hand cannot be null");
        if (!actionHand.isHand()) throw new IllegalArgumentException("Equipment slot must be a hand");
        for (StatInstance instance : statMap.getInstances()) playerStats.put(instance.getStat(), instance.getFinal(actionHand));
    }

    @Override public MMOPlayerData getData() { return playerData; }
    @Override public EquipmentSlot getActionHand() { return actionHand; }

    @Override
    public double getStat(String id) {
        return playerStats.getOrDefault(id, playerData.getStatMap().getInstance(id).getBase());
    }

    public void setStat(String id, double value) { playerStats.put(id, value); }

    public AttackMetadata attack(LivingEntity target, double damage, List<DamageType> damageTypes) {
        return attack(target, damage, true, damageTypes);
    }

    /** The boolean is knockback, exactly as MythicLib 1.7.1. */
    public AttackMetadata attack(LivingEntity target, double damage, boolean knockback, List<DamageType> damageTypes) {
        AttackMetadata registered = MythicLib.plugin.getDamage().getRegisteredAttackMetadata(target);
        if (registered != null) {
            registered.getDamage().add(damage, damageTypes);
            return registered;
        }
        AttackMetadata attack = new AttackMetadata(new DamageMetadata(damage, damageTypes), target, this);
        MythicLib.plugin.getDamage().registerAttack(attack, knockback, false);
        return attack;
    }

    public AttackMetadata attack(LivingEntity target, double damage, DamageType... damageTypes) {
        return attack(target, damage, Arrays.asList(damageTypes));
    }

    /** The boolean is knockback, exactly as MythicLib 1.7.1. */
    public AttackMetadata attack(LivingEntity target, double damage, boolean knockback, DamageType... damageTypes) {
        return attack(target, damage, knockback, Arrays.asList(damageTypes));
    }

    @Override public PlayerMetadata cache(EquipmentSlot slot) { return this; }
}
