package vn.svframe.svframelib.fabric.runtime.script;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Mutable execution context for native SVFrameLib scripts. */
public final class ScriptContext {
    /** Live bridge used by combat scripts to mutate the real damage metadata. */
    public interface DamageBridge {
        double total();
        double type(String type);
        double element(String element);
        void setTotal(double amount);
        void setType(String type, double amount);
        void setElement(String element, double amount);
        void multiplyAll(double coefficient);
        void multiplyType(String type, double coefficient);
        void multiplyElement(String element, double coefficient);
        void additiveAll(double multiplier);
        void additiveType(String type, double multiplier);
    }

    @FunctionalInterface
    public interface StringResolver {
        String resolve(String input);
    }

    /** Bridge to SVFrameLib's SKILL/PROFILE/PLAYER/SERVER variable scopes. */
    public interface VariableBridge {
        boolean exists(String path);
        Object get(String path);
        Vector3 vector(String path);
        void set(String scope, String name, Object value);
    }

    private final UUID caster;
    private UUID target;
    private double damage;
    private boolean cancelled;
    private Vector3 sourceLocation;
    private Vector3 targetLocation;
    private DamageBridge damageBridge;
    private StringResolver stringResolver = input -> input == null ? "" : input;
    private VariableBridge variableBridge;
    private final Map<String, Double> numbers = new HashMap<>();
    private final Map<String, Vector3> vectors = new HashMap<>();
    private final Map<String, Object> objects = new HashMap<>();
    private final Set<String> damageTypes = new HashSet<>();
    private final Map<String, Double> damageByType = new HashMap<>();
    private final Map<String, Double> damageByElement = new HashMap<>();

    public ScriptContext(UUID caster, UUID target) {
        this.caster = Objects.requireNonNull(caster, "caster");
        this.target = target;
    }

    public UUID caster() { return caster; }
    public UUID target() { return target; }
    public void target(UUID value) { target = value; }

    public double damage() { return damageBridge == null ? damage : damageBridge.total(); }
    public void damage(double value) {
        damage = value;
        numbers.put("attack.damage", value);
        if (damageBridge != null) damageBridge.setTotal(value);
    }

    public double damage(String type) {
        String normalized = normalize(type);
        return damageBridge == null ? damageByType.getOrDefault(normalized, 0.0d) : damageBridge.type(normalized);
    }

    public void damage(String type, double value) {
        String normalized = normalize(type);
        damageByType.put(normalized, value);
        numbers.put("attack.damage_" + normalized.toLowerCase(java.util.Locale.ROOT), value);
        if (damageBridge != null) damageBridge.setType(normalized, value);
    }

    public double elementDamage(String element) {
        String normalized = normalize(element);
        return damageBridge == null ? damageByElement.getOrDefault(normalized, 0.0d) : damageBridge.element(normalized);
    }

    public void elementDamage(String element, double value) {
        String normalized = normalize(element);
        damageByElement.put(normalized, value);
        numbers.put("attack.element_" + normalized.toLowerCase(java.util.Locale.ROOT), value);
        if (damageBridge != null) damageBridge.setElement(normalized, value);
    }

    public boolean cancelled() { return cancelled; }
    public void cancel() { cancelled = true; }
    public void uncancel() { cancelled = false; }

    public Map<String, Double> numbers() { return numbers; }
    public Map<String, Vector3> vectors() { return vectors; }
    public Map<String, Object> objects() { return objects; }
    public Set<String> damageTypes() { return damageTypes; }
    public Map<String, Double> damageByType() { return damageByType; }
    public Map<String, Double> damageByElement() { return damageByElement; }

    public Vector3 sourceLocation() { return sourceLocation; }
    public void sourceLocation(Vector3 value) { sourceLocation = value; }
    public Vector3 targetLocation() { return targetLocation; }
    public void targetLocation(Vector3 value) { targetLocation = value; }

    public DamageBridge damageBridge() { return damageBridge; }
    public void bindDamageBridge(DamageBridge bridge) { damageBridge = bridge; }

    public void bindStringResolver(StringResolver resolver) {
        stringResolver = Objects.requireNonNull(resolver, "resolver");
    }

    public String resolve(String input) {
        return stringResolver.resolve(input);
    }

    public void bindVariableBridge(VariableBridge bridge) {
        variableBridge = bridge;
    }

    public boolean hasVariable(String path) {
        if (path == null || path.isBlank()) return false;
        if (variableBridge != null && variableBridge.exists(path)) return true;
        return numbers.containsKey(path) || vectors.containsKey(path) || objects.containsKey(path);
    }

    public Object variable(String path) {
        if (path == null || path.isBlank()) return null;
        if (variableBridge != null) {
            Object value = variableBridge.get(path);
            if (value != null) return value;
        }
        if (numbers.containsKey(path)) return numbers.get(path);
        if (vectors.containsKey(path)) return vectors.get(path);
        return objects.get(path);
    }

    public Vector3 vectorVariable(String path) {
        if (path == null || path.isBlank()) return null;
        Vector3 direct = vectors.get(path);
        if (direct != null) return direct;
        return variableBridge == null ? null : variableBridge.vector(path);
    }

    public void setVariable(String scope, String name, Object value) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Variable name cannot be blank");
        String actualScope = scope == null || scope.isBlank() ? "SKILL" : scope;
        if (actualScope.equalsIgnoreCase("SKILL") || variableBridge == null) {
            if (value instanceof Number number) numbers.put(name, number.doubleValue());
            else if (value instanceof Vector3 vector) vectors.put(name, vector);
            else objects.put(name, value);
        }
        if (variableBridge != null) variableBridge.set(actualScope, name, value);
    }

    public ScriptContext copy() {
        ScriptContext copy = new ScriptContext(caster, target);
        copy.damage = damage;
        copy.cancelled = cancelled;
        copy.sourceLocation = sourceLocation;
        copy.targetLocation = targetLocation;
        copy.damageBridge = damageBridge;
        copy.stringResolver = stringResolver;
        copy.variableBridge = variableBridge;
        copy.numbers.putAll(numbers);
        copy.vectors.putAll(vectors);
        copy.objects.putAll(objects);
        copy.damageTypes.addAll(damageTypes);
        copy.damageByType.putAll(damageByType);
        copy.damageByElement.putAll(damageByElement);
        return copy;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
