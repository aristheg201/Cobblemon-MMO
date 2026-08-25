package io.lumine.mythic.lib.manager;

import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.module.Module;
import io.lumine.mythic.lib.script.Script;
import io.lumine.mythic.lib.script.condition.Condition;
import io.lumine.mythic.lib.script.condition.RawCondition;
import io.lumine.mythic.lib.script.mechanic.Mechanic;
import io.lumine.mythic.lib.script.mechanic.RawMechanic;
import io.lumine.mythic.lib.script.targeter.EntityTargeter;
import io.lumine.mythic.lib.script.targeter.LocationTargeter;
import io.lumine.mythic.lib.skill.handler.MythicLibSkillHandler;
import io.lumine.mythic.lib.skill.handler.ScriptSkillHandler;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import io.lumine.mythic.lib.skill.handler.SkillHandlerSource;
import io.lumine.mythic.lib.util.configobject.ConfigObject;
import io.lumine.mythic.lib.util.configobject.MapConfigObject;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Native Fabric implementation of MythicLib 1.7.1's skill/script registry. */
public class SkillManager extends Module implements MMOManager {
    private static final String UNIDENTIFIED_SCRIPT_ID = "UnidentifiedScript";

    private final Map<String, Function<ConfigObject, Mechanic>> mechanics = new ConcurrentHashMap<>();
    private final Map<String, Function<ConfigObject, Condition>> conditions = new ConcurrentHashMap<>();
    private final Map<String, Function<ConfigObject, EntityTargeter>> entityTargets = new ConcurrentHashMap<>();
    private final Map<String, Function<ConfigObject, LocationTargeter>> locationTargets = new ConcurrentHashMap<>();
    private final Map<String, Script> scripts = new ConcurrentHashMap<>();
    private final Map<String, SkillHandler<?>> handlers = new ConcurrentHashMap<>();
    private final Map<String, Class<? extends SkillHandler<?>>> builtInSkillHandlerTypes = new ConcurrentHashMap<>();
    private final Map<String, SkillHandlerSource> skillHandlerSources = new ConcurrentHashMap<>();
    private volatile boolean registration = true;

    public SkillManager(MMOPlugin plugin) {
        super(plugin, "skill");
        registerNativeBuiltins();
    }

    public void registerSkillHandlerSource(SkillHandlerSource source) {
        Objects.requireNonNull(source, "Skill handler type cannot be null");
        String key = norm(source.getKey());
        if (skillHandlerSources.putIfAbsent(key, source) != null)
            throw new IllegalArgumentException("A skill source with the same ID already exists: " + source.getKey());
    }

    public SkillHandler<?> loadSkillHandler(Object input) {
        return loadSkillHandler(UNIDENTIFIED_SCRIPT_ID, input);
    }

    public SkillHandler<?> loadSkillHandler(String id, Object input) {
        Objects.requireNonNull(input, "Input cannot be null");
        if (input instanceof SkillHandler<?> handler) return handler;
        if (input instanceof Script script) return new MythicLibSkillHandler(script);
        if (input instanceof List<?> list) return new MythicLibSkillHandler(loadScript(id, list));
        if (input instanceof ConfigObject config) {
            String source = config.getString("source", "");
            if (!source.isBlank()) return sourceHandler(config, source);
            String type = config.getString("type", id);
            Class<? extends SkillHandler<?>> builtin = builtInSkillHandlerTypes.get(enumName(type));
            if (builtin != null) return instantiateBuiltin(builtin, config);
            Script existingScript = scripts.get(norm(type));
            if (existingScript != null) return new MythicLibSkillHandler(existingScript);
            return new ScriptSkillHandler(type, config);
        }
        if (input instanceof Map<?, ?> raw) return loadSkillHandler(id, mapObject(id, raw));
        if (input instanceof String value) {
            String text = value.trim();
            int colon = text.indexOf(':');
            if (colon > 0) {
                SkillHandlerSource source = skillHandlerSources.get(norm(text.substring(0, colon)));
                if (source != null) return source.getConstructor().apply(new MapConfigObject(id, Map.of("source", text)), text.substring(colon + 1));
            }
            SkillHandler<?> registered = handlers.get(norm(text));
            if (registered != null) return registered;
            Script script = scripts.get(norm(text));
            if (script != null) return new MythicLibSkillHandler(script);
            Class<? extends SkillHandler<?>> builtin = builtInSkillHandlerTypes.get(enumName(text));
            if (builtin != null) return instantiateBuiltin(builtin, new MapConfigObject(id, Map.of("type", text)));
            // Native Fabric can also dispatch skill IDs loaded from MythicLib's exact default skill bundle.
            return new ScriptSkillHandler(text);
        }
        throw new IllegalArgumentException("Unsupported skill handler input type " + input.getClass().getSimpleName());
    }

    private SkillHandler<?> sourceHandler(ConfigObject config, String sourceText) {
        int colon = sourceText.indexOf(':');
        if (colon <= 0 || colon == sourceText.length() - 1)
            throw new IllegalArgumentException("Source must be in the format 'source:InternalSkillName'");
        String key = norm(sourceText.substring(0, colon));
        SkillHandlerSource source = skillHandlerSources.get(key);
        if (source == null) throw new IllegalArgumentException("Could not find skill handler source '" + key + "'");
        return source.getConstructor().apply(config, sourceText.substring(colon + 1));
    }

    public SkillHandler<?> getHandlerOrThrow(String id) {
        return Objects.requireNonNull(getHandler(id), "Could not find skill handler with ID '" + id + "'");
    }

    public SkillHandler<?> getHandler(String id) { return handlers.get(norm(id)); }

    public Script loadScript(Object input) { return loadScript(UNIDENTIFIED_SCRIPT_ID, input); }

    public Script loadScript(String id, Object input) {
        Objects.requireNonNull(input, "Input cannot be null");
        if (input instanceof Script script) return script;
        if (input instanceof String scriptId) return getScriptOrThrow(scriptId);
        if (input instanceof List<?> list) {
            if (id == null) throw new IllegalArgumentException("Cannot use unidentified script here");
            List<String> mechanics = new ArrayList<>(list.size());
            for (Object value : list) mechanics.add(String.valueOf(value));
            return new Script(id, mechanics);
        }
        if (input instanceof ConfigObject cfg) {
            List<String> conditionLines = csvOrIndexed(cfg, "conditions");
            List<String> mechanicLines = csvOrIndexed(cfg, "mechanics");
            boolean pub = cfg.getBoolean("public", true);
            return new Script(id == null ? Objects.requireNonNullElse(cfg.getKey(), UNIDENTIFIED_SCRIPT_ID) : id, pub, conditionLines, mechanicLines);
        }
        if (input instanceof Map<?, ?> raw) return loadScript(id, mapObject(id, raw));
        throw new IllegalArgumentException("Unsupported script input type " + input.getClass().getSimpleName());
    }

    public Script getScriptOrThrow(String id) {
        return Objects.requireNonNull(getScript(id), "Could not find script with ID '" + id + "'");
    }
    public Script getScript(String id) { return scripts.get(norm(id)); }

    public Condition loadCondition(ConfigObject config) {
        Objects.requireNonNull(config, "config");
        Function<ConfigObject, Condition> factory = conditions.get(norm(config.getKey()));
        if (factory != null) return factory.apply(config);
        return new RawCondition(render(config.getKey(), config));
    }

    public Mechanic loadMechanic(ConfigObject config) {
        Objects.requireNonNull(config, "config");
        Function<ConfigObject, Mechanic> factory = mechanics.get(norm(config.getKey()));
        if (factory != null) return factory.apply(config);
        return new RawMechanic(render(config.getKey(), config));
    }

    public EntityTargeter loadEntityTargeter(ConfigObject config) {
        Objects.requireNonNull(config, "config");
        Function<ConfigObject, EntityTargeter> factory = entityTargets.get(norm(config.getKey()));
        if (factory == null) throw new IllegalArgumentException("Could not find entity targeter '" + config.getKey() + "'");
        return factory.apply(config);
    }

    public LocationTargeter loadLocationTargeter(ConfigObject config) {
        Objects.requireNonNull(config, "config");
        Function<ConfigObject, LocationTargeter> factory = locationTargets.get(norm(config.getKey()));
        if (factory == null) throw new IllegalArgumentException("Could not find location targeter '" + config.getKey() + "'");
        return factory.apply(config);
    }

    public void registerSkillHandler(SkillHandler<?> handler) {
        Objects.requireNonNull(handler, "handler");
        String key = norm(handler.getId());
        if (handlers.putIfAbsent(key, handler) != null) throw new IllegalArgumentException("A skill handler with ID '" + handler.getId() + "' already exists");
    }
    public Collection<SkillHandler<?>> getHandlers() { return List.copyOf(handlers.values()); }

    public void registerScript(Script script) {
        Objects.requireNonNull(script, "script");
        String key = norm(script.getId());
        if (scripts.putIfAbsent(key, script) != null) throw new IllegalArgumentException("A script with ID '" + script.getId() + "' already exists");
    }
    public Collection<Script> getScripts() { return List.copyOf(scripts.values()); }

    public void registerCondition(String id, Function<ConfigObject, Condition> factory, String... aliases) {
        putAliases(conditions, id, factory, aliases);
    }
    public void registerMechanic(String id, Function<ConfigObject, Mechanic> factory, String... aliases) {
        putAliases(mechanics, id, factory, aliases);
    }
    public void registerEntityTargeter(String id, Function<ConfigObject, EntityTargeter> factory, String... aliases) {
        putAliases(entityTargets, id, factory, aliases);
    }
    public void registerLocationTargeter(String id, Function<ConfigObject, LocationTargeter> factory, String... aliases) {
        putAliases(locationTargets, id, factory, aliases);
    }

    public void registerBuiltinSkillHandlerSource(Class<? extends SkillHandler<?>> type) { registerBuiltinSkillHandlerType(type); }
    public void registerBuiltinSkillHandlerType(Class<? extends SkillHandler<?>> type) {
        Objects.requireNonNull(type, "type");
        String key = enumName(type.getSimpleName());
        if (builtInSkillHandlerTypes.putIfAbsent(key, type) != null)
            throw new IllegalArgumentException("A builtin skill handler type with ID '" + key + "' already exists");
    }

    @Override public void initialize(boolean clear) {
        if (clear) {
            handlers.clear(); scripts.clear(); mechanics.clear(); conditions.clear(); entityTargets.clear(); locationTargets.clear();
            builtInSkillHandlerTypes.clear(); skillHandlerSources.clear();
            registerNativeBuiltins();
        }
        registration = false;
    }

    public boolean isRegistrationOpen() { return registration; }

    private void registerNativeBuiltins() {
        // The native engine recognizes all original aliases; these factories preserve the public registry surface.
        String[] conditionIds = {"boolean","compare","has_variable","in_between","string_contains","string_equals","biome","cuboid","distance","world","can_target","cooldown","food","has_ammo","has_damage_type","is_living","on_fire","permission","random_chance","time"};
        for (String id : conditionIds) conditions.put(id, cfg -> new RawCondition(render(id, cfg)));
        String[] mechanicIds = {"feed","heal","reduce_cooldown","saturate","add_stat_modifier","remove_stat_modifier","close_inventory","go_back","apply_cooldown","call_trigger","cancel_event","consume_ammo","dispatch_command","entity_effect","lightning_strike","script","teleport","velocity","additive_damage_buff","damage","mark_crit","multiply_damage","potion","remove_potion","set_no_damage_ticks","set_on_fire","give_item","kick","sudo","shoot_arrow","shulker_bullet","helix","line","parabola","projectile","raytrace","raytrace_blocks","raytrace_entities","slash","sphere","increment","set_boolean","set_double","set_integer","set_string","set_vector","add_vector","copy_vector","cross_product","dot_product","hadamard_product","multiply_vector","normalize_vector","orient_vector","set_x","set_y","set_z","subtract_vector","action_bar","particle","player_sound","tell"};
        for (String id : mechanicIds) mechanics.put(id, cfg -> new RawMechanic(render(id, cfg)));
    }

    private static <T> void putAliases(Map<String, T> map, String id, T value, String... aliases) {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(value, "factory");
        if (map.putIfAbsent(norm(id), value) != null) throw new IllegalArgumentException("Duplicate registry ID '" + id + "'");
        if (aliases != null) for (String alias : aliases) if (alias != null && !alias.isBlank()) map.putIfAbsent(norm(alias), value);
    }

    private static SkillHandler<?> instantiateBuiltin(Class<? extends SkillHandler<?>> type, ConfigObject cfg) {
        try {
            Constructor<? extends SkillHandler<?>> constructor = type.getDeclaredConstructor(ConfigObject.class);
            constructor.setAccessible(true);
            return constructor.newInstance(cfg);
        } catch (ReflectiveOperationException ignored) {
            try {
                Constructor<? extends SkillHandler<?>> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (ReflectiveOperationException second) {
                throw new IllegalArgumentException("Could not instantiate builtin skill handler " + type.getName(), second);
            }
        }
    }

    private static List<String> csvOrIndexed(ConfigObject cfg, String key) {
        if (!cfg.contains(key)) return List.of();
        String raw = cfg.getString(key, "").trim();
        if (raw.isEmpty()) return List.of();
        if (raw.indexOf('\n') >= 0) return raw.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
        return List.of(raw);
    }

    private static MapConfigObject mapObject(String key, Map<?, ?> raw) {
        Map<String, Object> map = new LinkedHashMap<>();
        raw.forEach((k, v) -> map.put(String.valueOf(k), v));
        return new MapConfigObject(key, map);
    }

    private static String render(String id, ConfigObject cfg) {
        StringBuilder out = new StringBuilder(id == null ? "" : id);
        if (cfg == null || cfg.getKeys().isEmpty()) return out.toString();
        out.append('{'); boolean first = true;
        for (String key : cfg.getKeys()) {
            if (!first) out.append(';'); first = false;
            out.append(key).append('=').append(cfg.getString(key, ""));
        }
        return out.append('}').toString();
    }

    private static String norm(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static String enumName(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
}
