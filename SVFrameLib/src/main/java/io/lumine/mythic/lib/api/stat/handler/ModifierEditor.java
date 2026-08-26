package io.lumine.mythic.lib.api.stat.handler;

import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;

@FunctionalInterface
public interface ModifierEditor {
    StatModifier apply(StatInstance instance, StatModifier modifier);
}
