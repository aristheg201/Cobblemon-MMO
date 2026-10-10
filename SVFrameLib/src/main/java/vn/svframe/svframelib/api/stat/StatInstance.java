package vn.svframe.svframelib.api.stat;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.stat.api.ModifiedInstance;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;
import vn.svframe.svframelib.fabric.SVFrameLibStatMod;
import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

/** Public stat instance backed one-to-one by the native Fabric stat engine. */
public final class StatInstance extends ModifiedInstance<StatModifier> {
    private final StatMap map;
    private final String stat;
    private final NativeStatEngine.StatInstance nativeInstance;
    private final AtomicBoolean updatePending = new AtomicBoolean(false);

    StatInstance(StatMap map, String stat) {
        this(map, stat, SVFrameLibStatMod.engine().instance(map.getData().getUniqueId(), stat));
    }

    StatInstance(StatMap map, String stat, NativeStatEngine.StatInstance nativeInstance) {
        this.map = Objects.requireNonNull(map);
        this.stat = Objects.requireNonNull(stat);
        this.nativeInstance = Objects.requireNonNull(nativeInstance);
    }

    public StatMap getMap() { return map; }
    public String getStat() { return stat; }
    public double getBase() { return nativeInstance.base(); }
    public double getDefaultBase() { return nativeInstance.defaultBase(); }
    public double getFinal() { return getFinal(EquipmentSlot.MAIN_HAND); }
    public double getFinal(EquipmentSlot slot) { return nativeInstance.finalValue(NativeStatEngine.EquipmentSlot.valueOf(slot.name())); }
    public String formatFinal() { return nativeInstance.formatFinal(); }
    public String format(double value) { return nativeInstance.format(value); }

    @Override
    public StatModifier getModifier(UUID id) {
        NativeStatEngine.Modifier nativeModifier = nativeInstance.modifier(id);
        return nativeModifier == null ? null : StatModifier.fromNative(stat, nativeModifier);
    }

    @Override
    public StatModifier getModifier(String key) {
        for (StatModifier modifier : getModifiers())
            if (Objects.equals(modifier.getKey(), key)) return modifier;
        return null;
    }

    @Override
    public Collection<StatModifier> getModifiers() {
        List<StatModifier> out = new ArrayList<>();
        for (NativeStatEngine.Modifier modifier : nativeInstance.modifiers())
            out.add(StatModifier.fromNative(stat, modifier));
        return List.copyOf(out);
    }

    @Override
    public Set<UUID> getIds() {
        return Set.copyOf(nativeInstance.modifierIds());
    }

    @Override
    public Set<String> getKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (StatModifier modifier : getModifiers()) keys.add(modifier.getKey());
        return keys;
    }

    public double getTotal() { return nativeInstance.total(); }
    public double getTotal(EquipmentSlot slot) { return nativeInstance.total(NativeStatEngine.EquipmentSlot.valueOf(slot.name())); }
    public double getTotal(double base) { return nativeInstance.total(base, NativeStatEngine.EquipmentSlot.MAIN_HAND); }
    public double getTotal(double base, EquipmentSlot slot) { return nativeInstance.total(base, NativeStatEngine.EquipmentSlot.valueOf(slot.name())); }

    @Override
    public void registerModifier(StatModifier modifier) {
        nativeInstance.register(modifier.toNative());
        update();
    }

    @Override
    public void addModifier(StatModifier modifier) {
        removeIf(modifier.getKey()::equals);
        registerModifier(modifier);
    }

    @Override
    public void removeModifier(UUID id) {
        if (nativeInstance.remove(id) != null) update();
    }

    @Override
    public void remove(String key) {
        if (nativeInstance.removeIf(modifier -> Objects.equals(modifier.key(), key)) > 0) update();
    }

    @Override
    public void removeIf(Predicate<String> predicate) {
        if (nativeInstance.removeIf(modifier -> predicate.test(modifier.key())) > 0) update();
    }

    @Override
    public boolean isEmpty() { return nativeInstance.isEmpty(); }

    @Override
    public boolean contains(String key) { return getModifier(key) != null; }

    public void invalidateReferences() { }

    /**
     * Mirrors the 1.7.1 pending-update contract for public stat listeners. The
     * native engine separately owns the authoritative Fabric attribute update;
     * StatMap buffers both layers so neither publishes intermediate batch state.
     */
    public void update() {
        if (map.isBufferingUpdates()) updatePending.set(true);
        else broadcastUpdate();
    }

    public void releaseUpdates() {
        if (updatePending.getAndSet(false)) broadcastUpdate();
    }

    private void broadcastUpdate() {
        if (SVFrameLib.plugin != null) SVFrameLib.plugin.getStats().runUpdate(this);
    }

    public double getFilteredTotal(double base,
                                   Predicate<StatModifier> filter,
                                   Function<StatModifier, StatModifier> editor) {
        double flat = base;
        double additive = 1d;
        double relative = 1d;
        for (StatModifier original : getModifiers()) {
            if (!filter.test(original)) continue;
            StatModifier modifier = editor.apply(original);
            if (modifier == null) continue;
            switch (modifier.getType()) {
                case FLAT -> flat += modifier.getValue();
                case ADDITIVE_MULTIPLIER -> additive += modifier.getValue() / 100d;
                case RELATIVE -> relative *= 1d + modifier.getValue() / 100d;
            }
        }
        return flat * additive * relative;
    }
}
