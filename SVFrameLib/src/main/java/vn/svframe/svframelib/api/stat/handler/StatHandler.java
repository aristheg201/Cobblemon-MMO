package vn.svframe.svframelib.api.stat.handler;

import vn.svframe.svframelib.MythicLib;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.stat.StatInstance;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Native Fabric implementation of MythicLib 1.7.1 stat-handler semantics. */
public class StatHandler {
    protected final boolean hasMinValue;
    protected final boolean hasMaxValue;
    protected final double baseValue;
    protected final double minValue;
    protected final double maxValue;
    protected final DecimalFormat decimalFormat;
    protected final String stat;
    private final List<StatUpdateListener> updates = new CopyOnWriteArrayList<>();
    private ModifierEditor modifierEditor;
    protected boolean updateOnLogin;

    public StatHandler(String stat) {
        this(stat, 0d, null, null,
                MythicLib.plugin == null ? new DecimalFormat("0.#") : MythicLib.plugin.getMMOConfig().newDecimalFormat("0.#"));
    }

    public StatHandler(String stat, double baseValue, Double minValue, Double maxValue, DecimalFormat format) {
        this.stat = Objects.requireNonNull(stat, "stat");
        this.baseValue = baseValue;
        this.hasMinValue = minValue != null;
        this.hasMaxValue = maxValue != null;
        this.minValue = minValue == null ? 0d : minValue;
        this.maxValue = maxValue == null ? 0d : maxValue;
        if (hasMinValue && hasMaxValue && this.minValue > this.maxValue) throw new IllegalArgumentException("minValue must be <= maxValue");
        this.decimalFormat = (DecimalFormat) Objects.requireNonNull(format, "format").clone();
    }

    public String getStat() { return stat; }
    public DecimalFormat getDecimalFormat() { return (DecimalFormat) decimalFormat.clone(); }
    public void addUpdateListener(StatUpdateListener update) { updates.add(Objects.requireNonNull(update)); }
    public void setModifierEditor(ModifierEditor editor) { modifierEditor = editor; }
    public ModifierEditor getModifierEditor() { return modifierEditor; }

    public void delegateTo(String targetStat) {
        addUpdateListener(instance -> {
            if (MythicLib.plugin != null) MythicLib.plugin.getStats().runUpdate(instance.getMap(), targetStat);
        });
    }

    public void runUpdates(StatInstance instance) {
        for (StatUpdateListener update : updates) update.onUpdate(instance);
    }

    public double getBaseValue(StatInstance instance) { return baseValue; }
    public double getPlayerDefaultBase() { return 0d; }
    public boolean updateOnLogin() { return updateOnLogin; }
    public double getFinalValue(StatInstance instance) { return getFinalValue(instance, EquipmentSlot.MAIN_HAND); }
    public double getFinalValue(StatInstance instance, EquipmentSlot actionHand) { return instance.getTotal(actionHand); }

    public double clampValue(double value) {
        if (hasMaxValue && value > maxValue) value = maxValue;
        if (hasMinValue && value < minValue) value = minValue;
        return value;
    }

    public boolean forcesUpdates() { return updateOnLogin || !updates.isEmpty(); }

    public StatModifier edit(StatInstance instance, StatModifier modifier) {
        return modifierEditor == null ? modifier : modifierEditor.apply(instance, modifier);
    }
}
