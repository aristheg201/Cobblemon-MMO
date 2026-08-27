package vn.svframe.svframelib.damage;

import vn.svframe.svframelib.element.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mutable packet-level damage metadata used throughout the native SVFrame combat API. */
public class DamageMetadata implements Cloneable {
    private final List<DamagePacket> packets = new ArrayList<>();
    private final DamagePacket initialPacket;
    private final List<String> critTags = new ArrayList<>();

    public DamageMetadata(double damage, List<DamageType> types) {
        this(damage, null, types);
    }

    public DamageMetadata(double damage, Element element, List<DamageType> types) {
        this(new DamagePacket(damage, element, Objects.requireNonNull(types, "Damage types cannot be null")));
    }

    public DamageMetadata(DamagePacket initialPacket) {
        this.initialPacket = Objects.requireNonNull(initialPacket, "Initial packet cannot be null");
        packets.add(initialPacket);
    }

    public DamagePacket getInitialPacket() { return initialPacket; }

    /** Original 1.7.1 floor: zero-damage metadata is reserved for fake/check events. */
    public static final double MINIMAL_DAMAGE = .01d;

    public double getDamage() {
        double total = 0d;
        for (DamagePacket packet : packets) total += packet.getFinalValue();
        return Math.max(MINIMAL_DAMAGE, total);
    }

    public double getDamage(Element element) {
        double total = 0d;
        for (DamagePacket packet : packets)
            if (Objects.equals(packet.getElement(), element)) total += packet.getFinalValue();
        return total;
    }

    public double getDamage(DamageType type) {
        double total = 0d;
        for (DamagePacket packet : packets) if (packet.hasType(type)) total += packet.getFinalValue();
        return total;
    }

    /** 1.7.1 exposes the mutable packet list; consumers use it to edit attack metadata in-place. */
    public List<DamagePacket> getPackets() { return packets; }

    public Set<DamageType> collectTypes() {
        Set<DamageType> collected = new HashSet<>();
        for (DamagePacket packet : packets) collected.addAll(packet.getTypes());
        return collected;
    }

    public boolean hasAnyType(List<DamageType> damageTypes) {
        for (DamageType candidate : damageTypes)
            for (DamagePacket packet : packets) if (packet.hasType(candidate)) return true;
        return false;
    }

    public boolean hasType(DamageType type) {
        for (DamagePacket packet : packets) if (packet.hasType(type)) return true;
        return false;
    }

    public boolean hasElement(Element element) {
        for (DamagePacket packet : packets)
            if (Objects.equals(packet.getElement(), element)) return true;
        return false;
    }

    public DamageMetadata add(double value, DamageType... types) { return add(value, Arrays.asList(types)); }
    public DamageMetadata add(double value, List<DamageType> types) {
        packets.add(new DamagePacket(value, types));
        return this;
    }
    public DamageMetadata add(double value, Element element, DamageType... types) { return add(value, element, Arrays.asList(types)); }
    public DamageMetadata add(double value, Element element, List<DamageType> types) {
        packets.add(new DamagePacket(value, element, types));
        return this;
    }

    public DamageMetadata multiplicativeModifier(double coefficient) {
        for (DamagePacket packet : packets) packet.multiplicativeModifier(coefficient);
        return this;
    }
    public DamageMetadata additiveModifier(double multiplier) {
        for (DamagePacket packet : packets) packet.additiveModifier(multiplier);
        return this;
    }
    public DamageMetadata multiplicativeModifier(double coefficient, DamageType damageType) {
        for (DamagePacket packet : packets) if (packet.hasType(damageType)) packet.multiplicativeModifier(coefficient);
        return this;
    }
    public DamageMetadata multiplicativeModifier(double coefficient, List<DamageType> damageTypes) {
        for (DamagePacket packet : packets) if (packet.hasAnyType(damageTypes)) packet.multiplicativeModifier(coefficient);
        return this;
    }
    public DamageMetadata multiplicativeModifier(double coefficient, Element element) {
        for (DamagePacket packet : packets)
            if (Objects.equals(packet.getElement(), element)) packet.multiplicativeModifier(coefficient);
        return this;
    }
    public DamageMetadata additiveModifier(double multiplier, DamageType damageType) {
        for (DamagePacket packet : packets) if (packet.hasType(damageType)) packet.additiveModifier(multiplier);
        return this;
    }
    public DamageMetadata additiveModifier(double coefficient, Element element) {
        for (DamagePacket packet : packets)
            if (Objects.equals(packet.getElement(), element)) packet.additiveModifier(coefficient);
        return this;
    }

    @Override
    public DamageMetadata clone() {
        DamageMetadata clone = new DamageMetadata(initialPacket.clone());
        for (int i = 1; i < packets.size(); i++) clone.packets.add(packets.get(i).clone());
        clone.critTags.addAll(critTags);
        return clone;
    }

    @Override public String toString() { return packets.toString(); }

    @Deprecated public DamageMetadata(double damage, DamageType... types) { this(damage, null, Arrays.asList(types)); }
    @Deprecated public DamageMetadata(double damage, Element element, DamageType... types) { this(damage, element, Arrays.asList(types)); }
    @Deprecated public DamageMetadata() { this(0d, List.of()); }

    @Deprecated
    public Set<Element> collectElements() {
        Set<Element> collected = new HashSet<>();
        for (DamagePacket packet : packets) if (packet.getElement() != null) collected.add(packet.getElement());
        return collected;
    }

    @Deprecated
    public Map<Element, Double> mapElementalDamage() {
        Map<Element, Double> mapped = new HashMap<>();
        for (DamagePacket packet : packets)
            if (packet.getElement() != null)
                mapped.put(packet.getElement(), mapped.getOrDefault(packet.getElement(), 0d) + packet.getFinalValue());
        return mapped;
    }

    @Deprecated public boolean isWeaponCriticalStrike() { return critTags.contains("weapon"); }
    @Deprecated public void registerWeaponCriticalStrike() { critTags.add("weapon"); }
    @Deprecated public boolean isSkillCriticalStrike() { return critTags.contains("skill"); }
    @Deprecated public void registerSkillCriticalStrike() { critTags.add("skill"); }
    @Deprecated public void registerCrits(List<String> tags) { if (tags != null) critTags.addAll(tags); }
    @Deprecated public boolean isElementalCriticalStrike(Element element) { return element != null && critTags.contains(element.getId()); }
    @Deprecated public void registerElementalCriticalStrike(Element element) { if (element != null) critTags.add(element.getId()); }
}
