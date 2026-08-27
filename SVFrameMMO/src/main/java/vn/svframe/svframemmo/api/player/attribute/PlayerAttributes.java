package vn.svframe.svframemmo.api.player.attribute;

import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Owns all attribute instances for one player. */
public final class PlayerAttributes {
    private final PlayerData data;
    private final Map<String, AttributeInstance> instances = new LinkedHashMap<>();

    public PlayerAttributes(PlayerData data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    public PlayerData getData() { return data; }

    public void reload() {
        instances.values().forEach(AttributeInstance::refresh);
    }

    public void load(Map<String, ? extends Number> values) {
        instances.clear();
        if (values == null) return;
        for (Map.Entry<String, ? extends Number> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            AttributeInstance instance = getInstance(entry.getKey());
            instance.setBase(entry.getValue().intValue());
        }
    }

    public int getAttribute(PlayerAttribute attribute) { return getInstance(attribute).getTotal(); }
    public int getAttribute(String attribute) { return getInstance(attribute).getTotal(); }
    public Collection<AttributeInstance> getInstances() { return java.util.List.copyOf(instances.values()); }

    public Map<String, Integer> mapPoints() {
        Map<String, Integer> result = new HashMap<>();
        for (AttributeInstance instance : instances.values()) {
            PlayerAttribute attribute = instance.getAttribute();
            if (attribute != null && !attribute.isSaved()) continue;
            result.put(instance.getAttributeId(), instance.getBase());
        }
        return result;
    }

    public AttributeInstance getInstance(String attribute) {
        String id = PlayerAttribute.normalizeId(attribute);
        return instances.computeIfAbsent(id, ignored -> new AttributeInstance(data, id));
    }

    public AttributeInstance getInstance(PlayerAttribute attribute) {
        return getInstance(attribute.getId());
    }

    public int countPoints() {
        int total = 0;
        for (AttributeInstance instance : instances.values()) total += instance.getBase();
        return total;
    }

    public void setBaseAttribute(String id, int value) {
        getInstance(id).setBase(value);
    }
}
