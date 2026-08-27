package vn.svframe.svframeitems.model;

import java.util.*;

public record ItemAbility(Trigger trigger, String skill, double chance, int cooldownTicks, Map<String,Double> parameters) {
    public enum Trigger { ATTACK, USE, EQUIP }
    public ItemAbility {
        Objects.requireNonNull(trigger, "trigger");
        skill = Objects.requireNonNull(skill, "skill").trim();
        if (skill.isEmpty()) throw new IllegalArgumentException("skill cannot be empty");
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("chance must be 0..1");
        if (cooldownTicks < 0) throw new IllegalArgumentException("cooldownTicks must be >= 0");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
