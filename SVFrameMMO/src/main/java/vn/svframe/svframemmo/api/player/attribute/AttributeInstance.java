package vn.svframe.svframemmo.api.player.attribute;

import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.stat.StatInstance;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;
import vn.svframe.svframelib.player.modifier.ModifierSource;
import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Per-player attribute state backed by SVFrameLib's native stat engine. */
public final class AttributeInstance {
    public static final String MODIFIER_KEY = "svframemmo_attribute";
    private static final UUID BASE_MODIFIER_UNIQUE_ID = UUID.nameUUIDFromBytes("svframemmo:attribute:base".getBytes(StandardCharsets.UTF_8));

    private final PlayerData owner;
    private final String attributeId;
    private final String statId;
    private final Map<String, AppliedBuff> appliedBuffs = new LinkedHashMap<>();
    private int spent;

    private record AppliedBuff(String stat, UUID id) {}

    public AttributeInstance(PlayerData owner, String attributeId) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.attributeId = PlayerAttribute.normalizeId(attributeId);
        this.statId = asSVFrameLibStat(this.attributeId);
    }

    public PlayerData getData() { return owner; }
    public String getAttributeId() { return attributeId; }
    public PlayerAttribute getAttribute() { return SVFrameMMO.attributes().get(attributeId); }
    public static String asSVFrameLibStat(String attributeId) { return "SVFRAMEMMO_" + UtilityMethods.enumName(attributeId); }

    private StatInstance getHandle() {
        return owner.getMMOPlayerData().getStatMap().getInstance(statId);
    }

    public void setBase(int value) {
        int effective = Math.max(0, value);
        if (effective == spent) return;
        spent = effective;
        if (owner.getPlayer() == null) return;

        // MythicLib's original AttributeSource propagates StatProxy changes into the
        // player stat map. The native Fabric port applies the equivalent modifiers
        // directly, so it must explicitly release a buffered stat-map update here.
        // Without this, attribute points are persisted but vanilla/native stats stay stale.
        owner.getMMOPlayerData().getStatMap().bufferUpdates(() -> {
            updateBaseModifier();
            refreshBuffsInternal();
        });
    }

    public void addBase(int value) { setBase(spent + value); }
    public int getBase() { return spent; }
    public int getTotal() { return owner.getPlayer() == null ? spent : (int) getHandle().getTotal(); }

    public void refresh() {
        if (owner.getPlayer() == null) return;
        owner.getMMOPlayerData().getStatMap().bufferUpdates(() -> {
            updateBaseModifier();
            refreshBuffsInternal();
        });
    }

    private void updateBaseModifier() {
        if (owner.getPlayer() == null) return;
        if (spent == 0) {
            getHandle().removeModifier(BASE_MODIFIER_UNIQUE_ID);
            return;
        }
        getHandle().registerModifier(new StatModifier(
                BASE_MODIFIER_UNIQUE_ID,
                MODIFIER_KEY,
                statId,
                spent,
                ModifierType.FLAT,
                EquipmentSlot.OTHER,
                ModifierSource.OTHER));
    }

    /** Apply configured attribute buffs as native stat modifiers and publish them immediately. */
    public void refreshBuffs() {
        if (owner.getPlayer() == null) return;
        owner.getMMOPlayerData().getStatMap().bufferUpdates(this::refreshBuffsInternal);
    }

    private void refreshBuffsInternal() {
        if (owner.getPlayer() == null) return;
        for (AppliedBuff old : appliedBuffs.values()) {
            owner.getMMOPlayerData().getStatMap().getInstance(old.stat()).removeModifier(old.id());
        }
        appliedBuffs.clear();

        PlayerAttribute attribute = getAttribute();
        if (attribute == null) return;
        double total = getHandle().getTotal();
        int index = 0;
        for (PlayerAttribute.Buff buff : attribute.getBuffs()) {
            String key = buff.stat() + "#" + index++;
            UUID id = UUID.nameUUIDFromBytes((owner.getUniqueId() + ":" + attributeId + ":" + key).getBytes(StandardCharsets.UTF_8));
            String modifierKey = MODIFIER_KEY + ":" + attributeId;
            owner.getMMOPlayerData().getStatMap().getInstance(buff.stat()).registerModifier(new StatModifier(
                    id,
                    modifierKey,
                    buff.stat(),
                    total * buff.value(),
                    buff.type(),
                    EquipmentSlot.OTHER,
                    ModifierSource.OTHER));
            appliedBuffs.put(key, new AppliedBuff(buff.stat(), id));
        }
    }
}
