package vn.svframe.svframelib.damage;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.provider.StatProvider;
import vn.svframe.svframelib.player.PlayerMetadata;

import java.util.Arrays;
import java.util.Objects;

/** Native Fabric form of SVFrameLib 1.7.1 AttackMetadata. */
public class AttackMetadata {
    private final DamageMetadata damage;
    private final LivingEntity target;
    private final StatProvider attacker;

    @Deprecated
    public AttackMetadata(DamageMetadata damage, StatProvider attacker) { this(damage, null, attacker); }

    public AttackMetadata(DamageMetadata damage, LivingEntity target, StatProvider attacker) {
        this.attacker = attacker;
        this.target = target;
        this.damage = Objects.requireNonNull(damage, "Damage cannot be null");
    }

    public DamageMetadata getDamage() { return damage; }
    public LivingEntity getTarget() { return target; }
    public StatProvider getAttacker() { return attacker; }
    public boolean hasAttacker() { return attacker != null; }
    public boolean isPlayer() { return attacker instanceof vn.svframe.svframelib.api.stat.provider.PlayerStatProvider; }

    @Deprecated public boolean hasExpired() { return false; }
    @Deprecated public void expire() { }
    @Deprecated @Override public AttackMetadata clone() { return new AttackMetadata(damage.clone(), target, attacker); }

    @Deprecated public void damage(LivingEntity entity) { damage(entity, true); }

    /** @deprecated The boolean is knockback, matching SVFrameLib 1.7.1. */
    @Deprecated public void damage(LivingEntity entity, boolean knockback) {
        SVFrameLib.plugin.getDamage().damage(this, entity, knockback);
    }

    @Deprecated public ServerPlayerEntity getPlayer() { return playerMetadata().getPlayer(); }
    @Deprecated public MMOPlayerData getData() { return playerMetadata().getData(); }
    @Deprecated public double getStat(String id) { return requireAttacker().getStat(id); }
    @Deprecated public void setStat(String id, double value) { playerMetadata().setStat(id, value); }
    @Deprecated public AttackMetadata attack(LivingEntity target, double damage, DamageType... damageTypes) { return playerMetadata().attack(target, damage, Arrays.asList(damageTypes)); }

    /** @deprecated The boolean is knockback, matching SVFrameLib 1.7.1. */
    @Deprecated public AttackMetadata attack(LivingEntity target, double damage, boolean knockback, DamageType... damageTypes) {
        return playerMetadata().attack(target, damage, knockback, Arrays.asList(damageTypes));
    }

    private StatProvider requireAttacker() {
        if (attacker == null) throw new IllegalArgumentException("No attacker was found");
        return attacker;
    }

    private PlayerMetadata playerMetadata() {
        StatProvider provider = requireAttacker();
        if (!(provider instanceof PlayerMetadata metadata)) throw new IllegalArgumentException("Attacker is not a player");
        return metadata;
    }
}
