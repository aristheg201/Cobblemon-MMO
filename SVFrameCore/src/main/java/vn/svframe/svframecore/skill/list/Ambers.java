package vn.svframe.svframecore.skill.list;

import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import vn.svframe.svframecore.api.player.PlayerData;
import vn.svframe.svframecore.fabric.SVFrameCoreFabricMod;
import vn.svframe.svframelib.api.event.PlayerAttackEvent;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.player.skill.PassiveSkill;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.BuiltinSkillHandler;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.def.SimpleSkillResult;
import vn.svframe.svframelib.util.ParabolicProjectile;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.concurrent.ThreadLocalRandom;

@BuiltinSkillHandler(mods = {"percent"}, triggerable = false)
public final class Ambers extends SkillHandler<SimpleSkillResult> {
    private static final DustParticleEffect ORANGE = new DustParticleEffect(new Vector3f(1f, 0.647f, 0f), 1.3f);

    public Ambers() { super("AMBERS"); }
    public Ambers(ConfigObject config) { super("AMBERS", config); }

    @Override
    public SimpleSkillResult getResult(SkillMetadata meta) {
        return new SimpleSkillResult(meta != null && meta.hasAttackSource() && meta.getTargetLivingEntityOrNull() != null);
    }

    @Override
    public void whenCast(SimpleSkillResult result, SkillMetadata meta) {
        if (!result.isSuccessful()) return;
        LivingEntity target = meta.getTargetLivingEntityOrNull();
        if (target == null || !(target.getWorld() instanceof ServerWorld world)) return;

        Vec3d source = target.getPos().add(0d, target.getHeight() * .5d, 0d);
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2d);
        Vec3d amberLocation = target.getPos().add(4d * Math.cos(angle), 0d, 4d * Math.sin(angle));
        MMOPlayerData data = meta.getCaster().getData();
        double percent = meta.getParameter("percent");

        new ParabolicProjectile(world, source, amberLocation,
                () -> SVFrameCoreFabricMod.schedule(0, new AmberPickup(data, world, amberLocation, percent)),
                1,
                point -> world.spawnParticles(ORANGE, point.x, point.y, point.z, 1, 0d, 0d, 0d, 0d));
    }

    public void onPlayerAttack(PlayerAttackEvent event) {
        MMOPlayerData data = event.getAttacker().getData();
        if (!event.getAttack().getDamage().hasType(DamageType.SKILL)) return;
        PassiveSkill passive = data.getPassiveSkillMap().getSkill(this);
        if (passive != null) passive.getTriggeredSkill().cast(SkillMetadata.of(event));
    }

    private static final class AmberPickup implements Runnable {
        private final MMOPlayerData data;
        private final ServerWorld world;
        private final Vec3d location;
        private final double percent;
        private int iterations;

        private AmberPickup(MMOPlayerData data, ServerWorld world, Vec3d location, double percent) {
            this.data = data;
            this.world = world;
            this.location = location;
            this.percent = percent / 100d;
        }

        @Override
        public void run() {
            if (iterations++ > 66 || !data.isOnline()) return;
            ServerPlayerEntity player = data.getPlayer();
            if (player.getWorld() != world) return;

            if (player.getPos().add(0d, 1d, 0d).squaredDistanceTo(location) < 3d) {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1f, 1f);
                PlayerData core = PlayerData.get(data);
                double missingMana = Math.max(0d, data.getStatMap().getStat("MAX_MANA") - core.getMana());
                core.giveMana(missingMana * percent, ResourceUpdateReason.SKILL);
                return;
            }

            world.spawnParticles(ORANGE, location.x, location.y, location.z, 6, 0d, 0d, 0d, 0d);
            SVFrameCoreFabricMod.schedule(3, this);
        }
    }
}
