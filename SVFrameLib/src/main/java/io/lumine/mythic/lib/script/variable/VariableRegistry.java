package io.lumine.mythic.lib.script.variable;

/** Registry used to resolve nested MythicLib script variables. */
public interface VariableRegistry<T> {
    Variable<?> accessVariable(T variable, String path);

    /** Compatibility alias retained for the early Fabric source surface. */
    default Variable<?> get(T variable, String path) {
        return accessVariable(variable, path);
    }

    default String stringify(T variable) {
        return String.valueOf(variable);
    }
}
