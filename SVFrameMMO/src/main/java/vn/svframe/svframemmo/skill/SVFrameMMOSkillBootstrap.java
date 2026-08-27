package vn.svframe.svframemmo.skill;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframelib.manager.SkillManager;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframelib.util.configobject.MapConfigObject;
import vn.svframe.svframemmo.script.mechanic.ManaMechanic;
import vn.svframe.svframemmo.script.mechanic.StaminaMechanic;
import vn.svframe.svframemmo.script.mechanic.StelliumMechanic;
import vn.svframe.svframemmo.skill.list.Ambers;
import vn.svframe.svframemmo.skill.list.Neptune_Gift;
import vn.svframe.svframemmo.skill.list.Sneaky_Picky;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

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
        skills.registerMechanic("mana", ManaMechanic::new);
        skills.registerMechanic("stamina", StaminaMechanic::new);
        skills.registerMechanic("stellium", StelliumMechanic::new);

        registerHandler(skills, new Ambers(config(dir, "ambers.yml", "AMBERS")));
        registerHandler(skills, new Neptune_Gift(config(dir, "neptune-gift.yml", "NEPTUNE_GIFT")));
        registerHandler(skills, new Sneaky_Picky(config(dir, "sneaky-picky.yml", "SNEAKY_PICKY")));

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

            manager.registerSkillHandler(new NativeAliasSkillHandler(new MapConfigObject(aliasId, section), target));
            loaded++;
        }
        return loaded;
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

    /**
     * Keeps the alias ID, UI metadata and parameter formulas while executing the real native source handler.
     * Raw dispatch is safe because getResult() returns the exact result implementation expected by the same delegate.
     */
    private static final class NativeAliasSkillHandler extends SkillHandler<SkillResult> {
        private final SkillHandler<?> delegate;

        private NativeAliasSkillHandler(MapConfigObject config, SkillHandler<?> delegate) {
            super(config);
            this.delegate = delegate;
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
