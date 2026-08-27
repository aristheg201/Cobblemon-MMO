package vn.svframe.svframelib.script.variable;

import vn.svframe.svframelib.util.lang3.Validate;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Native implementation of SVFrameLib 1.7.1 nested variable registries. */
public class SimpleVariableRegistry<D> implements VariableRegistry<Variable<D>> {
    private final Map<String, Function<D, Variable<?>>> registered = new HashMap<>();
    private final SimpleVariableRegistry<?> parent;

    public SimpleVariableRegistry() {
        this(null);
    }

    public SimpleVariableRegistry(SimpleVariableRegistry<?> parent) {
        this.parent = parent;
    }

    @Override
    public Variable<?> accessVariable(Variable<D> variable, String path) {
        Objects.requireNonNull(variable, "variable");
        if (path == null || path.isBlank()) return variable;
        String key = path.toLowerCase(Locale.ROOT);

        Function<D, Variable<?>> supplier = registered.get(key);
        if (supplier != null) return supplier.apply(variable.getStored());

        SimpleVariableRegistry<?> current = parent;
        while (current != null) {
            @SuppressWarnings("unchecked")
            Function<D, Variable<?>> inherited = (Function<D, Variable<?>>) (Function<?, ?>) current.registered.get(key);
            if (inherited != null) return inherited.apply(variable.getStored());
            current = current.parent;
        }

        throw new IllegalArgumentException("Unknown variable path '" + path + "' for " + variable.getClass().getSimpleName());
    }

    public <E extends D> void transferTo(SimpleVariableRegistry<E> target) {
        Objects.requireNonNull(target, "target");
        registered.forEach((name, supplier) -> target.registered.put(name,
                value -> supplier.apply(value)));
    }

    public void registerVariable(String name,
                                 Function<D, Variable<?>> supplier,
                                 String... aliases) {
        Validate.notBlank(name, "Variable name cannot be blank");
        Validate.notNull(supplier, "Supplier cannot be null");
        String key = name.toLowerCase(Locale.ROOT);
        Validate.isTrue(!registered.containsKey(key), "Variable '%s' is already registered", name);
        registered.put(key, supplier);
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias == null || alias.isBlank()) continue;
                registerVariable(alias, supplier);
            }
        }
    }
}
