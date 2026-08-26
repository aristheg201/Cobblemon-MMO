package io.lumine.mythic.lib.script.variable;

import java.util.Objects;

public abstract class Variable<D> implements VariableContainer {
    private final String name;
    private D stored;

    public Variable(String name, D stored) {
        this.name = Objects.requireNonNull(name, "name");
        this.stored = stored;
    }

    public String getName() { return name; }
    public D getStored() { return stored; }
    public void setStored(D value) { stored = value; }
    public abstract VariableRegistry<Variable<D>> getVariableRegistry();

    public Variable<?> getVariable(String path) {
        return getVariableRegistry().accessVariable(this, path);
    }

    @Override
    public String toString() {
        return String.valueOf(stored);
    }
}
