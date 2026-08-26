package io.lumine.mythic.lib.script.variable.def;

import io.lumine.mythic.lib.script.variable.Variable;
import io.lumine.mythic.lib.script.variable.VariableRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Dynamic view over a living entity's native Minecraft attributes. */
public class AttributesVariable extends Variable<LivingEntity> {
    public static final VariableRegistry<Variable<LivingEntity>> VARIABLE_REGISTRY = new VariableRegistry<>() {
        @Override
        public Variable<?> accessVariable(Variable<LivingEntity> variable, String path) {
            LivingEntity entity = variable.getStored();
            if (entity == null) throw new IllegalArgumentException("No entity available for attribute lookup");
            if (path == null || path.isBlank()) return variable;
            String raw = path.trim().toLowerCase(java.util.Locale.ROOT);
            Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
            if (id == null) throw new IllegalArgumentException("Invalid attribute '" + path + "'");
            var entry = Registries.ATTRIBUTE.getEntry(id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown attribute '" + path + "'"));
            return new DoubleVariable("temp", entity.getAttributeValue(entry));
        }
    };

    public AttributesVariable(String name, LivingEntity value) { super(name, value); }
    @Override public VariableRegistry<Variable<LivingEntity>> getVariableRegistry() { return VARIABLE_REGISTRY; }
    @Override public String toString() { return getStored() == null ? "None" : getStored().getUuidAsString(); }
}
