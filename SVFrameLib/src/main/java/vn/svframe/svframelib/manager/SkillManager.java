package vn.svframe.svframelib.manager;

import vn.svframe.svframelib.module.MMOPlugin;
import vn.svframe.svframelib.module.Module;
import vn.svframe.svframelib.script.Script;
import vn.svframe.svframelib.script.condition.Condition;
import vn.svframe.svframelib.script.condition.RawCondition;
import vn.svframe.svframelib.script.mechanic.Mechanic;
import vn.svframe.svframelib.script.mechanic.RawMechanic;
import vn.svframe.svframelib.script.targeter.EntityTargeter;
import vn.svframe.svframelib.script.targeter.LocationTargeter;
import vn.svframe.svframelib.script.targeter.entity.*;
import vn.svframe.svframelib.script.targeter.location.CasterLocationTargeter;
import vn.svframe.svframelib.script.targeter.location.CircleLocationTargeter;
import vn.svframe.svframelib.script.targeter.location.CustomLocationTargeter;
import vn.svframe.svframelib.script.targeter.location.SourceLocationTargeter;
import vn.svframe.svframelib.script.targeter.location.TargetEntityLocationTargeter;
import vn.svframe.svframelib.script.targeter.location.TargetLocationTargeter;
import vn.svframe.svframelib.script.targeter.location.VariableLocationTargeter;
import vn.svframe.svframelib.skill.handler.MythicLibSkillHandler;
import vn.svframe.svframelib.skill.handler.ScriptSkillHandler;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.handler.SkillHandlerSource;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import vn.svframe.svframelib.util.configobject.MapConfigObject;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Native Fabric implementation of the MythicLib 1.7.1 skill/script registry. */
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
        Objects.requireNonNull(source, "Skill handler source cannot be null");
        String key = norm(source.getKey());
        if (skillHandlerSources.putIfAbsent(key, source) != null)
            throw new IllegalArgumentException("A skill source with the same ID already exists: " + source.getKey());
    }

    public SkillHandler<?> loadSkillHandler(Object input) { return loadSkillHandler(UNIDENTIFIED_SCRIPT_ID, input); }

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
                if (source != null)
                    return source.getConstructor().apply(new MapConfigObject(id, Map.of("source", text)), text.substring(colon + 1));
            }
            SkillHandler<?> registered = handlers.get(norm(text));
            if (registered != null) return registered;
            Script script = scripts.get(norm(text));
            if (script != null) return new MythicLibSkillHandler(script);
            Class<? extends SkillHandler<?>> builtin = builtInSkillHandlerTypes.get(enumName(text));
            if (builtin != null) return instantiateBuiltin(builtin, new MapConfigObject(id, Map.of("type", text)));
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
    public Collection<SkillHandler<?>> getHandlers() { return List.copyOf(handlers.values()); }

    public Script loadScript(Object input) { return loadScript(UNIDENTIFIED_SCRIPT_ID, input); }

    public Script loadScript(String id, Object input) {
        Objects.requireNonNull(input, "Input cannot be null");
        if (input instanceof Script script) return script;
        if (input instanceof String scriptId) return getScriptOrThrow(scriptId);
        if (input instanceof List<?> list) {
            if (id == null) throw new IllegalArgumentException("Cannot use unidentified script here");
            List<String> mechanicLines = new ArrayList<>(list.size());
            for (Object value : list) mechanicLines.add(String.valueOf(value));
            return new Script(id, mechanicLines);
        }
        if (input instanceof ConfigObject config) {
            List<String> conditionLines = lines(config, "conditions");
            List<String> mechanicLines = lines(config, "mechanics");
            boolean pub = config.getBoolean("public", true);
            return new Script(id == null ? Objects.requireNonNullElse(config.getKey(), UNIDENTIFIED_SCRIPT_ID) : id,
                    pub, conditionLines, mechanicLines);
        }
        if (input instanceof Map<?, ?> raw) return loadScript(id, mapObject(id, raw));
        throw new IllegalArgumentException("Unsupported script input type " + input.getClass().getSimpleName());
    }

    public Script getScriptOrThrow(String id) {
        return Objects.requireNonNull(getScript(id), "Could not find script with ID '" + id + "'");
    }
    public Script getScript(String id) { return scripts.get(norm(id)); }
    public Collection<Script> getScripts() { return List.copyOf(scripts.values()); }

    private static String findEffectiveObjectType(String objectType, ConfigObject config) {
        if (config.contains("type")) return norm(config.getString("type"));
        if (config.hasKey()) return norm(config.getKey());
        throw new IllegalArgumentException("Could not find " + objectType + " type");
    }

    public Condition loadCondition(ConfigObject config) {
        Objects.requireNonNull(config, "config");
        String key = findEffectiveObjectType("condition", config);
        Function<ConfigObject, Condition> factory = conditions.get(key);
        if (factory == null) throw new IllegalArgumentException("Could not match condition to '" + key + "'");
        return factory.apply(config);
    }

    public Mechanic loadMechanic(ConfigObject config) {
        Objects.requireNonNull(config, "config");
        String key = findEffectiveObjectType("mechanic", config);
        Function<ConfigObject, Mechanic> factory = mechanics.get(key);
        if (factory == null) throw new IllegalArgumentException("Could not match mechanic to '" + key + "'");
        return factory.apply(config);
    }

    public EntityTargeter loadEntityTargeter(ConfigObject config) {
        Objects.requireNonNull(config, "config");
        String key = findEffectiveObjectType("targeter", config);
        Function<ConfigObject, EntityTargeter> factory = entityTargets.get(key);
        if (factory == null) throw new IllegalArgumentException("Could not match targeter to '" + key + "'");
        return factory.apply(config);
    }

    public LocationTargeter loadLocationTargeter(ConfigObject config) {
        Objects.requireNonNull(config, "config");
        String key = findEffectiveObjectType("targeter", config);
        Function<ConfigObject, LocationTargeter> factory = locationTargets.get(key);
        if (factory == null) throw new IllegalArgumentException("Could not match targeter to '" + key + "'");
        return factory.apply(config);
    }

    public void registerSkillHandler(SkillHandler<?> handler) {
        Objects.requireNonNull(handler, "handler");
        String key = norm(handler.getId());
        if (handlers.putIfAbsent(key, handler) != null)
            throw new IllegalArgumentException("A skill handler with ID '" + handler.getId() + "' already exists");
    }

    public void registerScript(Script script) {
        Objects.requireNonNull(script, "script");
        String key = norm(script.getId());
        if (scripts.putIfAbsent(key, script) != null)
            throw new IllegalArgumentException("A script with ID '" + script.getId() + "' already exists");
    }

    public void registerCondition(String id, Function<ConfigObject, Condition> factory, String... aliases) { putAliases(conditions, id, factory, aliases); }
    public void registerMechanic(String id, Function<ConfigObject, Mechanic> factory, String... aliases) { putAliases(mechanics, id, factory, aliases); }
    public void registerEntityTargeter(String id, Function<ConfigObject, EntityTargeter> factory, String... aliases) { putAliases(entityTargets, id, factory, aliases); }
    public void registerLocationTargeter(String id, Function<ConfigObject, LocationTargeter> factory, String... aliases) { putAliases(locationTargets, id, factory, aliases); }

    public void registerBuiltinSkillHandlerSource(Class<? extends SkillHandler<?>> type) { registerBuiltinSkillHandlerType(type); }
    public void registerBuiltinSkillHandlerType(Class<? extends SkillHandler<?>> type) {
        Objects.requireNonNull(type, "type");
        String key = enumName(type.getSimpleName());
        if (builtInSkillHandlerTypes.putIfAbsent(key, type) != null)
            throw new IllegalArgumentException("A builtin skill handler type with ID '" + key + "' already exists");
    }

    @Override
    public synchronized void initialize(boolean clear) {
        if (clear) {
            handlers.clear(); scripts.clear(); mechanics.clear(); conditions.clear(); entityTargets.clear(); locationTargets.clear(); builtInSkillHandlerTypes.clear(); skillHandlerSources.clear();
            registerNativeBuiltins();
        }
        registration = false;
    }

    public synchronized void reload() { handlers.clear(); scripts.clear(); registration = true; }
    public boolean isRegistrationOpen() { return registration; }

    private void registerNativeBuiltins() {
        String[] conditionIds = {"boolean","compare","has_variable","in_between","string_contains","string_equals","biome","cuboid","distance","world","can_target","cooldown","food","has_ammo","has_damage_type","is_living","on_fire","permission","random_chance","time"};
        for (String id : conditionIds) conditions.put(id, cfg -> new RawCondition(render(id, cfg)));
        aliasCondition("can_target","can_tgt","cantarget","ctgt");
        aliasCondition("has_ammo","ammo");

        registerRawMechanic("add_stat","add_stat_modifier");
        registerRawMechanic("remove_stat","remove_stat_modifier");
        registerRawMechanic("feed"); registerRawMechanic("heal");
        registerRawMechanic("reduce_cooldown","reduce_cd","decrease_cooldown","decrease_cd"); registerRawMechanic("saturate");
        registerRawMechanic("apply_cooldown","apply_cd"); registerRawMechanic("consume_ammo","take_ammo"); registerRawMechanic("delay");
        registerRawMechanic("dispatch_command","c","dispatch_cmd","cmd","command","execute_command","execute_cmd","run_command","run_cmd");
        registerRawMechanic("entity_effect"); registerRawMechanic("lightning","lightning_strike"); registerRawMechanic("script","skill","cast");
        registerRawMechanic("teleport","tp","set_position","set_pos","setpos","setposition","set_location","setlocation","set_loc","setloc","move","moveto","move_to");
        registerRawMechanic("set_velocity","velocity","setvel","set_vel","setvelocity");
        registerRawMechanic("additive_damage_buff"); registerRawMechanic("damage","deal_damage","dmg","deal_dmg","dealdamage","dealdmg","attack","atk");
        registerRawMechanic("multiply_damage"); registerRawMechanic("potion"); registerRawMechanic("remove_potion"); registerRawMechanic("set_on_fire"); registerRawMechanic("set_no_damage_ticks"); registerRawMechanic("mark_crit");
        registerRawMechanic("give_item"); registerRawMechanic("sudo"); registerRawMechanic("kick"); registerRawMechanic("close_inventory"); registerRawMechanic("go_back"); registerRawMechanic("call_trigger"); registerRawMechanic("cancel_event");
        registerRawMechanic("shoot_arrow","fire_arrow","bowshoot","bow_shoot","shoot_bow"); registerRawMechanic("shulker_bullet");
        registerRawMechanic("raytrace_blocks"); registerRawMechanic("raytrace_entities");
        registerRawMechanic("draw_helix","helix"); registerRawMechanic("draw_line","line"); registerRawMechanic("draw_parabola","parabola","spawn_parabola"); registerRawMechanic("projectile"); registerRawMechanic("ray_trace","raytrace","cast_ray","ray_cast","raycast"); registerRawMechanic("slash"); registerRawMechanic("draw_sphere","sphere");
        registerRawMechanic("add_vector","add_vec"); registerRawMechanic("cross_product"); registerRawMechanic("dot_product"); registerRawMechanic("hadamard_product"); registerRawMechanic("multiply_vector"); registerRawMechanic("normalize_vector","normalize"); registerRawMechanic("orient_vector","orient_vec"); registerRawMechanic("save_vector","copy_vector","save_vec","copy_vec"); registerRawMechanic("set_x"); registerRawMechanic("set_y"); registerRawMechanic("set_z"); registerRawMechanic("subtract_vector","sub_vec","sub_vector","subvec");
        registerRawMechanic("increment","incr"); registerRawMechanic("set_boolean","set_bool"); registerRawMechanic("set_double","set_float"); registerRawMechanic("set_integer","set_int"); registerRawMechanic("set_string","set_str"); registerRawMechanic("set_vector","set_vec");
        registerRawMechanic("action_bar"); registerRawMechanic("particle","spawn_particle","par"); registerRawMechanic("sound","play_world_sound","play_sound","world_sound"); registerRawMechanic("player_sound","play_player_sound"); registerRawMechanic("tell","message","msg","send","send_message","send_msg");

        registerEntityTargeter("caster", cfg -> new CasterTargeter());
        registerEntityTargeter("cone", ConeTargeter::new);
        registerEntityTargeter("nearby_entities", NearbyEntitiesTargeter::new);
        registerEntityTargeter("nearest_entity", NearestEntityTargeter::new);
        registerEntityTargeter("target", cfg -> new TargetTargeter());
        registerEntityTargeter("variable", VariableEntityTargeter::new);
        registerEntityTargeter("looking_at", vn.svframe.svframelib.script.targeter.entity.LookingAtTargeter::new);

        registerLocationTargeter("caster", CasterLocationTargeter::new);
        registerLocationTargeter("circle", CircleLocationTargeter::new);
        registerLocationTargeter("custom", CustomLocationTargeter::new);
        registerLocationTargeter("looking_at", vn.svframe.svframelib.script.targeter.location.LookingAtTargeter::new);
        registerLocationTargeter("source_location", cfg -> new SourceLocationTargeter());
        registerLocationTargeter("target", TargetEntityLocationTargeter::new);
        registerLocationTargeter("target_location", cfg -> new TargetLocationTargeter());
        registerLocationTargeter("variable", VariableLocationTargeter::new);
    }

    private void registerRawMechanic(String id, String... aliases) {
        Function<ConfigObject, Mechanic> factory = cfg -> new RawMechanic(render(id, cfg));
        putAliases(mechanics,id,factory,aliases);
    }
    private void aliasCondition(String id, String... aliases) {
        Function<ConfigObject, Condition> factory = conditions.get(id); if(factory!=null) for(String alias:aliases) conditions.putIfAbsent(norm(alias),factory);
    }

    private static <T> void putAliases(Map<String, T> map, String id, T value, String... aliases) {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(value, "factory");
        if (map.putIfAbsent(norm(id), value) != null) throw new IllegalArgumentException("Duplicate registry ID '" + id + "'");
        if (aliases != null) for (String alias : aliases) if (alias != null && !alias.isBlank()) map.putIfAbsent(norm(alias), value);
    }

    private static SkillHandler<?> instantiateBuiltin(Class<? extends SkillHandler<?>> type, ConfigObject config) {
        try {
            Constructor<? extends SkillHandler<?>> constructor = type.getDeclaredConstructor(ConfigObject.class); constructor.setAccessible(true); return constructor.newInstance(config);
        } catch (ReflectiveOperationException ignored) {
            try { Constructor<? extends SkillHandler<?>> constructor = type.getDeclaredConstructor(); constructor.setAccessible(true); return constructor.newInstance(); }
            catch (ReflectiveOperationException second) { throw new IllegalArgumentException("Could not instantiate builtin skill handler " + type.getName(), second); }
        }
    }

    private static List<String> lines(ConfigObject config, String key) {
        if (!config.contains(key)) return List.of(); String raw = config.getString(key, "").trim(); if (raw.isEmpty()) return List.of();
        if (raw.indexOf('\n') >= 0) return raw.lines().map(String::trim).filter(s -> !s.isEmpty()).toList(); return List.of(raw);
    }

    private static MapConfigObject mapObject(String key, Map<?, ?> raw) { Map<String, Object> map = new LinkedHashMap<>(); raw.forEach((k, v) -> map.put(String.valueOf(k), v)); return new MapConfigObject(key, map); }

    private static String render(String id, ConfigObject config) {
        StringBuilder out = new StringBuilder(id == null ? "" : id); if (config == null || config.getKeys().isEmpty()) return out.toString(); out.append('{'); boolean first = true;
        for (String key : config.getKeys()) { if ("type".equals(key)) continue; if (!first) out.append(';'); first = false; out.append(key).append('=').append(config.getString(key, "")); }
        return out.append('}').toString();
    }

    private static String norm(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static String enumName(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
}
