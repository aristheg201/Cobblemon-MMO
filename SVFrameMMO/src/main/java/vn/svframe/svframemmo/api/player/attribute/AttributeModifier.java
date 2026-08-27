package vn.svframe.svframemmo.api.player.attribute;

import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;
import vn.svframe.svframelib.player.modifier.ModifierSource;
import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframemmo.SVFrameMMO;

import java.util.Objects;
import java.util.UUID;

/** Native SVFrameLib stat modifier targeting an SVFrameMMO attribute stat. */
public class AttributeModifier extends StatModifier {
    private final PlayerAttribute attribute;

    public AttributeModifier(UUID uniqueId, String key, PlayerAttribute attribute, double value,
                             ModifierType type, EquipmentSlot slot, ModifierSource source) {
        super(uniqueId, key, AttributeInstance.asSVFrameLibStat(attribute.getId()), value, type, slot, source);
        this.attribute = Objects.requireNonNull(attribute, "attribute");
    }

    public AttributeModifier(String key, PlayerAttribute attribute, double value, ModifierType type) {
        this(UUID.randomUUID(), key, attribute, value, type, EquipmentSlot.OTHER, ModifierSource.OTHER);
    }

    public PlayerAttribute getAttribute() { return attribute; }

    @Override
    public void register(MMOPlayerData data) {
        super.register(data);
        SVFrameMMO.playerData().get(data.getUniqueId()).getAttributes().getInstance(attribute).refreshBuffs();
    }

    @Override
    public void unregister(MMOPlayerData data) {
        super.unregister(data);
        SVFrameMMO.playerData().get(data.getUniqueId()).getAttributes().getInstance(attribute).refreshBuffs();
    }
}
