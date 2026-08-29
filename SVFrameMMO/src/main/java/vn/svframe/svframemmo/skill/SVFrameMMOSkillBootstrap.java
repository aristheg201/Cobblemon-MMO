package vn.svframe.svframemmo.skill;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframelib.manager.SkillManager;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.parameter.value.ScalingFormula;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframelib.util.configobject.MapConfigObject;
import vn.svframe.svframemmo.script.mechanic.ManaMechanic;
import vn.svframe.svframemmo.script.mechanic.StaminaMechanic;
import vn.svframe.svframemmo.script.mechanic.StelliumMechanic;
import vn.svframe.svframemmo.skill.list.Ambers;
import vn.svframe.svframemmo.skill.list.Neptune_Gift;
import vn.svframe.svframemmo.skill.list.Sneaky_Picky;
import vn.svframe.svframemmo.skill.list.Staff_Attack;

import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Registers native class skills plus validated aliases for legacy default-skill definitions. */
public final class SVFrameMMOSkillBootstrap {
    private static final String LEGACY_ALIAS_FILE = "legacy-class-aliases.yml";
    private static final int EXPECTED_NATIVE_ALIASES = 34;
    private static volatile int aliasCount;

    private SVFrameMMOSkillBootstrap() { }

    public static void register(Path dir) throws IOException {
        SkillManager skills = SVFrameLib.inst().getSkills();
        skills.registerBuiltinSkillHandlerType(Ambers.class);
        skills.registerBuiltinSkillHandlerType(Neptune_Gift.class);
        skills.registerBuiltinSkillHandlerType(Sneaky_Picky.class);
        skills.registerBuiltinSkillHandlerType(Staff_Attack.class);
        skills.registerMechanic("mana", ManaMechanic::new);
        skills.registerMechanic("stamina", StaminaMechanic::new);
        skills.registerMechanic("stellium", StelliumMechanic::new);

        registerHandler(skills, new Ambers(config(dir, "ambers.yml", "AMBERS")));
        registerHandler(skills, new Neptune_Gift(config(dir, "neptune-gift.yml", "NEPTUNE_GIFT")));
        registerHandler(skills, new Sneaky_Picky(config(dir, "sneaky-picky.yml", "SNEAKY_PICKY")));
        registerHandler(skills, new Staff_Attack(config(dir, "staff-attack.yml", "STAFF_ATTACK")));

        aliasCount = registerNativeAliases(skills, dir.resolve(LEGACY_ALIAS_FILE));
        if (aliasCount != EXPECTED_NATIVE_ALIASES)
            throw new IOException("Expected " + EXPECTED_NATIVE_ALIASES + " native class skill aliases, got " + aliasCount);
    }

    public static int aliasCount() { return aliasCount; }

    private static int registerNativeAliases(SkillManager manager, Path file) throws IOException {
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        int loaded = 0;

        for (Map.Entry<String, Object> entry : root.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> raw))
                throw new IOException("Skill alias '" + entry.getKey() + "' is not a configuration section");

            Map<String, Object> section = stringMap(raw);
            String source = String.valueOf(section.getOrDefault("source", "")).trim();
            int colon = source.indexOf(':');
            if (colon <= 0 || !source.substring(0, colon).trim().equalsIgnoreCase("default"))
                throw new IOException("Skill alias '" + entry.getKey() + "' must reference a native default skill");

            String aliasId = UtilityMethods.enumName(entry.getKey());
            String targetId = UtilityMethods.enumName(source.substring(colon + 1));
            if (aliasId.equals(targetId))
                throw new IOException("Skill alias must use a distinct ID: " + aliasId);
            if (manager.getHandler(aliasId) != null)
                throw new IOException("Skill alias collides with an existing handler: " + aliasId);

            SkillHandler<?> target = manager.getHandler(targetId);
            if (target == null)
                throw new IOException("Native source skill '" + targetId + "' required by alias '" + aliasId + "' is not registered");

            manager.registerSkillHandler(new NativeAliasSkillHandler(
                    new MapConfigObject(aliasId, section), target, configuredParameterIds(section)));
            loaded++;
        }
        return loaded;
    }

    private static Set<String> configuredParameterIds(Map<String, Object> section) {
        Object raw = section.get("parameters");
        if (!(raw instanceof Map<?, ?> parameters)) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        parameters.keySet().forEach(key -> result.add(String.valueOf(key)));
        return Set.copyOf(result);
    }

    private static MapConfigObject config(Path dir, String file, String id) throws IOException {
        return new MapConfigObject(id, YamlLite.map(YamlLite.parse(dir.resolve(file))));
    }

    private static void registerHandler(SkillManager manager, SkillHandler<?> handler) {
        if (manager.getHandler(handler.getId()) == null) manager.registerSkillHandler(handler);
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static final class NativeAliasSkillHandler extends SkillHandler<SkillResult> {
        private final SkillHandler<?> delegate;
        private final Set<String> configuredParameters;

        private NativeAliasSkillHandler(MapConfigObject config, SkillHandler<?> delegate, Set<String> configuredParameters) {
            super(config);
            this.delegate = delegate;
            this.configuredParameters = Set.copyOf(configuredParameters);
        }

        private boolean inherits(String id) {
            return !configuredParameters.contains(id) && delegate.getParameters().contains(id);
        }

        @Override
        public Set<String> getParameters() {
            LinkedHashSet<String> result = new LinkedHashSet<>(delegate.getParameters());
            result.addAll(super.getParameters());
            return Set.copyOf(result);
        }

        @Override
        public Set<String> getModifiers() {
            LinkedHashSet<String> result = new LinkedHashSet<>(delegate.getModifiers());
            result.addAll(super.getModifiers());
            return Set.copyOf(result);
        }

        @Override
        public String getParameterName(String id) {
            return inherits(id) ? delegate.getParameterName(id) : super.getParameterName(id);
        }

        @Override
        public double getDefaultItemParameter(String id) {
            return inherits(id) ? delegate.getDefaultItemParameter(id) : super.getDefaultItemParameter(id);
        }

        @Override
        public ScalingFormula getDefaultFormula(String id) {
            return inherits(id) ? delegate.getDefaultFormula(id) : super.getDefaultFormula(id);
        }

        @Override
        public DecimalFormat getParameterDecimalFormat(String id) {
            return inherits(id) ? delegate.getParameterDecimalFormat(id) : super.getParameterDecimalFormat(id);
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public SkillResult getResult(SkillMetadata metadata) {
            return (SkillResult) ((SkillHandler) delegate).getResult(metadata);
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void whenCast(SkillResult result, SkillMetadata metadata) {
            ((SkillHandler) delegate).whenCast(result, metadata);
        }
    }
}
