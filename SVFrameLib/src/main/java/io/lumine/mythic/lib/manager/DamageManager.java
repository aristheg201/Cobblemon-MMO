package io.lumine.mythic.lib.manager;

import io.lumine.mythic.lib.api.event.AttackEvent;
import io.lumine.mythic.lib.api.event.PlayerAttackEvent;
import io.lumine.mythic.lib.damage.AttackHandler;
import io.lumine.mythic.lib.damage.AttackMetadata;
import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Native Fabric implementation of MythicLib 1.7.1 attack metadata registration. */
public class DamageManager extends Module {
    private final List<AttackHandler> handlers = new ArrayList<>();
    private final Map<UUID,AttackMetadata> attackMetadatas = new WeakHashMap<>();

    public DamageManager(MMOPlugin plugin){ super(plugin,"damage"); }
    public void registerHandler(AttackHandler handler){ if(handler!=null)handlers.add(handler); }
    public List<AttackHandler> getHandlers(){ return List.copyOf(handlers); }
    public boolean registerAttack(AttackMetadata attack){ return registerAttack(attack,true,false); }
    public boolean registerAttack(AttackMetadata attack,boolean applyDamage){ return registerAttack(attack,applyDamage,false); }

    public boolean registerAttack(AttackMetadata attack,boolean applyDamage,boolean ignoreKnockback){
        if(attack==null||attack.getTarget()==null)throw new IllegalArgumentException("Target cannot be null");
        markAsMetadata(attack);
        try{
            AttackEvent event = attack.isPlayer() ? new PlayerAttackEvent(attack) : new AttackEvent(attack);
            event.call();
            if(event.isCancelled()) return false;
            if(!applyDamage) return true;
            LivingEntity attacker = attack.hasAttacker() ? attack.getAttacker().getEntity() : null;
            return applyDamage(attack.getDamage().getDamage(),attack.getTarget(),attacker);
        } finally { unmarkAsMetadata(attack.getTarget()); }
    }

    private boolean applyDamage(double amount,LivingEntity target,LivingEntity attacker){
        if(!Double.isFinite(amount)||amount<=0d||target==null||target.isDead())return false;
        DamageSource source;
        if(attacker instanceof ServerPlayerEntity player) source=target.getDamageSources().playerAttack(player);
        else if(attacker!=null) source=target.getDamageSources().mobAttack(attacker);
        else source=target.getDamageSources().generic();
        return target.damage(source,(float)Math.min(Float.MAX_VALUE,amount));
    }

    public AttackMetadata findAttack(Object event){
        if(event instanceof Entity entity)return getRegisteredAttackMetadata(entity);
        return null;
    }
    public AttackMetadata markAsMetadata(AttackMetadata attack){ return attackMetadatas.put(attack.getTarget().getUuid(),attack); }
    public AttackMetadata unmarkAsMetadata(Entity entity){ return entity==null?null:attackMetadatas.remove(entity.getUuid()); }
    public AttackMetadata getRegisteredAttackMetadata(Entity entity){ return entity==null?null:attackMetadatas.get(entity.getUuid()); }
    public void damage(AttackMetadata attack,LivingEntity target){ damage(attack,target,true); }
    public void damage(AttackMetadata attack,LivingEntity target,boolean applyDamage){ damage(attack,target,applyDamage,false); }
    public void damage(AttackMetadata attack,LivingEntity target,boolean applyDamage,boolean ignoreKnockback){
        AttackMetadata actual = attack.getTarget()==target?attack:new AttackMetadata(attack.getDamage(),target,attack.getAttacker());
        registerAttack(actual,applyDamage,ignoreKnockback);
    }
    public DamageMetadata findDamage(Object event){ AttackMetadata attack=findAttack(event);return attack==null?null:attack.getDamage(); }
    public void unmarkAsMetadata(AttackMetadata attack){ if(attack!=null&&attack.getTarget()!=null)unmarkAsMetadata(attack.getTarget()); }
}
