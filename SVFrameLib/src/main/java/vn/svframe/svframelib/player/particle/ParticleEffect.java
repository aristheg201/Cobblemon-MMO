package vn.svframe.svframelib.player.particle;

import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.modifier.ModifierMap;
import vn.svframe.svframelib.player.modifier.ModifierSource;
import vn.svframe.svframelib.player.modifier.PlayerModifier;
import vn.svframe.svframelib.util.Closeable;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Native scheduled particle effect base. */
public abstract class ParticleEffect extends PlayerModifier implements Closeable {
    protected final ParticleInformation particle;
    protected MMOPlayerData playerData;
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean started;

    public ParticleEffect(String key, ParticleInformation particle) {
        super(key, EquipmentSlot.OTHER, ModifierSource.OTHER);
        this.particle = Objects.requireNonNull(particle, "particle");
    }

    public ParticleEffect(ConfigObject obj) {
        super(obj.getString("key"), EquipmentSlot.OTHER, ModifierSource.OTHER);
        this.particle = ParticleInformation.fromConfig(obj.getObject("particle"));
    }

    public ParticleInformation getParticle() { return particle; }
    protected double resolveModifier(Map<String,Double> modifiers, String path) {
        return modifiers.getOrDefault(path, getType().getDefaultModifierValue(path));
    }

    public abstract void tick();
    public abstract ParticleEffectType getType();
    public boolean isStarted() { return started; }

    public ParticleEffect start() {
        if (started) return this;
        if (playerData == null) throw new IllegalStateException("Player data must be bound before starting a particle effect");
        started = true;
        long token = generation.incrementAndGet();
        scheduleNext(token, 0);
        return this;
    }

    private void scheduleNext(long token, int delay) {
        MythicLibFabricMod.schedule(delay, () -> {
            if (!started || generation.get() != token || playerData == null || !playerData.isOnline()) return;
            tick();
            scheduleNext(token, Math.max(1, getType().getPeriod()));
        });
    }

    public void stop() { started = false; generation.incrementAndGet(); }
    public void bindPlayerData(MMOPlayerData data) { this.playerData = Objects.requireNonNull(data); }

    @Override public void register(MMOPlayerData data) {
        bindPlayerData(data);
        data.getParticleEffectMap().addModifier(this);
        start();
    }
    @Override public void unregister(MMOPlayerData data) { data.getParticleEffectMap().removeModifier(getUniqueId()); }
    @Override public ModifierMap<?> getMap(MMOPlayerData data) { return data.getParticleEffectMap(); }
    @Override public void close() { stop(); }

    public static ParticleEffect fromConfig(ConfigObject obj) {
        ParticleEffectType type = ParticleEffectType.get(UtilityMethods.enumName(obj.getString("particle-effect")));
        return type.getParser().apply(obj);
    }
}
