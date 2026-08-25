package io.lumine.mythic.lib.damage;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.provider.StatProvider;
import io.lumine.mythic.lib.player.PlayerMetadata;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Arrays;
import java.util.Objects;

/** Native Fabric form of MythicLib 1.7.1 AttackMetadata. */
public class AttackMetadata {
    private final DamageMetadata damage;
    private final LivingEntity target;
    private final StatProvider attacker;

    public AttackMetadata(DamageMetadata damage, StatProvider attacker) {
        this(damage, null, attacker);
    }

    public AttackMetadata(DamageMetadata damage, LivingEntity target, StatProvider attacker) {
        this.attacker = attacker;
        this.target = target;
        this.damage = Objects.requireNonNull(damage, "Damage cannot be null");
    }

    public DamageMetadata getDamage() {
        return damage;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public StatProvider getAttacker() {
        return attacker;
    }

    public boolean hasAttacker() {
        return attacker != null;
    }

    public boolean isPlayer() {
        return attacker instanceof io.lumine.mythic.lib.api.stat.provider.PlayerStatProvider;
    }

    /** Kept for exact 1.7.1 API compatibility; attacks do not expire in this version. */
    public boolean hasExpired() {
        return false;
    }

    /** Kept for exact 1.7.1 API compatibility; attacks do not expire in this version. */
    public void expire() {
    }

    @Override
    public AttackMetadata clone() {
        return new AttackMetadata(damage.clone(), target, attacker);
    }

    public void damage(LivingEntity entity) {
        damage(entity, true);
    }

    public void damage(LivingEntity entity, boolean applyDamage) {
        MythicLib.plugin.getDamage().damage(this, entity, applyDamage);
    }

    public ServerPlayerEntity getPlayer() {
        return playerMetadata().getPlayer();
    }

    public MMOPlayerData getData() {
        return playerMetadata().getData();
    }

    public double getStat(String id) {
        return requireAttacker().getStat(id);
    }

    public void setStat(String id, double value) {
        playerMetadata().setStat(id, value);
    }

    public AttackMetadata attack(LivingEntity target, double damage, DamageType... damageTypes) {
        return playerMetadata().attack(target, damage, Arrays.asList(damageTypes));
    }

    public AttackMetadata attack(LivingEntity target, double damage, boolean registerDamage, DamageType... damageTypes) {
        return playerMetadata().attack(target, damage, registerDamage, Arrays.asList(damageTypes));
    }

    private StatProvider requireAttacker() {
        if (attacker == null) throw new IllegalArgumentException("No attacker was found");
        return attacker;
    }

    private PlayerMetadata playerMetadata() {
        StatProvider provider = requireAttacker();
        if (!(provider instanceof PlayerMetadata metadata)) {
            throw new IllegalArgumentException("Attacker is not a player");
        }
        return metadata;
    }
}
