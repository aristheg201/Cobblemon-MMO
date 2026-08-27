package vn.svframe.svframecore.skill.list;

import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import vn.svframe.svframecore.api.player.PlayerData;
import vn.svframe.svframelib.api.event.PlayerAttackEvent;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframelib.player.skill.PassiveSkill;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.BuiltinSkillHandler;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.def.SimpleSkillResult;
import vn.svframe.svframelib.util.configobject.ConfigObject;

@BuiltinSkillHandler(mods = {"extra"}, triggerable = false)
public final class Sneaky_Picky extends SkillHandler<SimpleSkillResult> {
    public Sneaky_Picky() { super("SNEAKY_PICKY"); }
    public Sneaky_Picky(ConfigObject config) { super("SNEAKY_PICKY", config); }

    @Override
    public SimpleSkillResult getResult(SkillMetadata meta) {
        return new SimpleSkillResult(meta != null && meta.hasAttackSource() && meta.getTargetLivingEntityOrNull() != null);
    }

    @Override
    public void whenCast(SimpleSkillResult result, SkillMetadata meta) {
        if (!result.isSuccessful()) return;
        LivingEntity target = meta.getTargetLivingEntityOrNull();
        if (target == null) return;
        meta.getAttackSource().getDamage().multiplicativeModifier(1d + meta.getParameter("extra") / 100d, DamageType.WEAPON);
        if (target.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.SMOKE,
                    target.getX(), target.getY() + target.getHeight() * .5d, target.getZ(),
                    64, 0d, 0d, 0d, .05d);
            world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.PLAYERS, 1f, 2f);
        }
    }

    public void onPlayerAttack(PlayerAttackEvent event) {
        MMOPlayerData data = event.getAttacker().getData();
        if (!event.getAttack().getDamage().hasType(DamageType.WEAPON)) return;
        if (PlayerData.get(data).isInCombat()) return;
        PassiveSkill passive = data.getPassiveSkillMap().getSkill(this);
        if (passive != null) passive.getTriggeredSkill().cast(SkillMetadata.of(event));
    }
}
