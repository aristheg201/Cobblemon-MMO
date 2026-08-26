package vn.svframe.svframelib.skill;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import vn.svframe.mythiclibfabric.runtime.script.ScriptContext;
import vn.svframe.mythiclibfabric.runtime.script.Vector3;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.script.variable.Variable;
import vn.svframe.svframelib.script.variable.VariableList;
import vn.svframe.svframelib.script.variable.def.BooleanVariable;
import vn.svframe.svframelib.script.variable.def.DoubleVariable;
import vn.svframe.svframelib.script.variable.def.IntegerVariable;
import vn.svframe.svframelib.script.variable.def.PositionVariable;
import vn.svframe.svframelib.script.variable.def.StringVariable;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.util.Position;

import java.util.Locale;
import java.util.UUID;

/** Adapts the 1.7.1 compatibility SkillMetadata to the dependency-free native script runtime. */
public final class SkillMetadataContextBridge {
    private SkillMetadataContextBridge() {}

    public static ScriptContext context(SkillMetadata metadata) {
        if (metadata == null || metadata.getCaster() == null)
            throw new IllegalArgumentException("Skill metadata/caster cannot be null");

        UUID caster = metadata.getCaster().getData().getUniqueId();
        var targetEntity = metadata.getTargetEntityOrNull();
        UUID target = targetEntity == null ? caster : targetEntity.getUuid();
        ScriptContext context = new ScriptContext(caster, target);
        context.sourceLocation(vector(metadata.getSourceLocation()));
        if (metadata.getTargetLocationOrNull() != null) context.targetLocation(vector(metadata.getTargetLocationOrNull()));
        context.bindStringResolver(metadata::parseString);
        context.bindVariableBridge(new MetadataVariableBridge(metadata));

        if (metadata.getCast() != null) {
            SkillHandler<?> handler = metadata.getCast().getHandler();
            if (handler != null) for (String key : handler.getParameters()) {
                double value = metadata.getParameter(key);
                context.numbers().put(key, value);
                context.numbers().put("parameter." + key, value);
                context.numbers().put("modifier." + key, value);
                context.objects().put(key, value);
                context.objects().put("parameter." + key, value);
                context.objects().put("modifier." + key, value);
            }
        }

        if (metadata.hasAttackSource()) context.objects().put("attack", metadata.getAttackSource());
        context.objects().put("skill_metadata", metadata);
        return context;
    }

    private static Vector3 vector(Vec3d value) {
        return new Vector3(value.x, value.y, value.z);
    }

    private static final class MetadataVariableBridge implements ScriptContext.VariableBridge {
        private final SkillMetadata metadata;

        private MetadataVariableBridge(SkillMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public boolean exists(String path) {
            try {
                metadata.getVariable(path);
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        @Override
        public Object get(String path) {
            try {
                Variable<?> variable = metadata.getVariable(path);
                return variable == null ? null : variable.getStored();
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        @Override
        public Vector3 vector(String path) {
            Object value = get(path);
            if (value instanceof Position position)
                return new Vector3(position.getX(), position.getY(), position.getZ());
            if (value instanceof Vec3d vector) return SkillMetadataContextBridge.vector(vector);
            return null;
        }

        @Override
        public void set(String scope, String name, Object value) {
            variableList(scope).registerVariable(variable(name, value));
        }

        private VariableList variableList(String scope) {
            String normalized = scope == null ? "SKILL" : scope.trim().toUpperCase(Locale.ROOT);
            MMOPlayerData data = metadata.getCaster().getData();
            return switch (normalized) {
                case "SKILL" -> metadata.getVariableList();
                case "PLAYER" -> data.getVariableList();
                case "PROFILE" -> data.getProfileSession().getVariableList();
                case "SERVER" -> VariableList.SERVER;
                default -> throw new IllegalArgumentException("Unknown variable scope: " + scope);
            };
        }

        private Variable<?> variable(String name, Object value) {
            if (value instanceof Variable<?> variable) return variable;
            if (value instanceof Boolean bool) return new BooleanVariable(name, bool);
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
                return new IntegerVariable(name, ((Number) value).intValue());
            if (value instanceof Number number) return new DoubleVariable(name, number.doubleValue());
            if (value instanceof Position position) return new PositionVariable(name, position);
            if (value instanceof Vector3 vector) {
                ServerWorld world = (ServerWorld) metadata.getCaster().getPlayer().getWorld();
                return new PositionVariable(name, new Position(world, vector.x(), vector.y(), vector.z()));
            }
            return new StringVariable(name, value == null ? "" : String.valueOf(value));
        }
    }
}
