package vn.svframe.svframelib.damage;

import vn.svframe.svframelib.element.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** One independently modifiable packet of an SVFrame attack. */
public class DamagePacket implements Cloneable {
    private List<DamageType> types;
    private double value;
    private double additive;
    private double scalar = 1d;
    private Element element;

    public DamagePacket(double value, List<DamageType> types) { this(value, null, types); }

    public DamagePacket(double value, Element element, List<DamageType> types) {
        this.value = value;
        this.types = Objects.requireNonNull(types, "Damage types cannot be null");
        this.element = element;
    }

    public double getValue() { return value; }
    public List<DamageType> getTypes() { return types; }
    public Element getElement() { return element; }

    public void setTypes(List<DamageType> types) { this.types = Objects.requireNonNullElse(types, List.of()); }

    public void setValue(double value) {
        if (!(value >= 0d)) throw new IllegalArgumentException("Value cannot be negative");
        this.value = value;
    }

    public void setElement(Element element) { this.element = element; }
    public void multiplicativeModifier(double coefficient) { scalar *= coefficient; }
    public void additiveModifier(double multiplier) { additive += multiplier; }

    public double getFinalValue() { return value * Math.max(0d, 1d + additive) * scalar; }

    public boolean hasType(DamageType type) {
        for (DamageType checked : types) if (checked == type) return true;
        return false;
    }

    public boolean hasAnyType(List<DamageType> damageTypes) {
        for (DamageType candidate : damageTypes) if (types.contains(candidate)) return true;
        return false;
    }

    @Override
    public DamagePacket clone() {
        DamagePacket clone = new DamagePacket(value, types);
        clone.additive = additive;
        clone.scalar = scalar;
        clone.element = element;
        return clone;
    }

    @Override public String toString() {
        return "DamagePacket{value=" + value + ", types=" + types + ", element=" + element
                + ", additive=" + additive + ", scalar=" + scalar + '}';
    }

    @Deprecated public DamagePacket(double value, DamageType... types) { this(value, null, Arrays.asList(types)); }
    @Deprecated public DamagePacket(double value, Element element, DamageType... types) { this(value, element, Arrays.asList(types)); }
    @Deprecated public void setTypes(DamageType[] types) { this.types = types == null ? List.of() : Arrays.asList(types); }
}
