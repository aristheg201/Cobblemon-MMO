package vn.svframe.svframelib.api.stat.handler;

import vn.svframe.svframelib.api.stat.StatInstance;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;

@FunctionalInterface
public interface ModifierEditor {
    StatModifier apply(StatInstance instance, StatModifier modifier);
}
