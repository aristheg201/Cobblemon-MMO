package vn.svframe.svframelib.manager;
import vn.svframe.svframelib.module.MMOPlugin;
import vn.svframe.svframelib.module.Module;
import vn.svframe.svframelib.script.Script;
import vn.svframe.svframelib.script.condition.Condition;
import vn.svframe.svframelib.script.condition.RawCondition;
import vn.svframe.svframelib.script.condition.generic.*;
import vn.svframe.svframelib.script.condition.location.*;
import vn.svframe.svframelib.script.condition.misc.*;
import vn.svframe.svframelib.script.mechanic.Mechanic;
import vn.svframe.svframelib.script.mechanic.RawMechanic;
import vn.svframe.svframelib.script.mechanic.buff.*;
import vn.svframe.svframelib.script.mechanic.buff.stat.*;
import vn.svframe.svframelib.script.mechanic.gui.*;
import vn.svframe.svframelib.script.mechanic.misc.*;
import vn.svframe.svframelib.script.mechanic.movement.*;
import vn.svframe.svframelib.script.mechanic.offense.*;
import vn.svframe.svframelib.script.mechanic.other.*;
import vn.svframe.svframelib.script.mechanic.projectile.*;
import vn.svframe.svframelib.script.mechanic.visual.*;
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
import vn.svframe.svframelib.skill.handler.SVFrameLibSkillHandler;
import vn.svframe.svframelib.skill.handler.BuiltinSkillHandler;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.handler.SkillHandlerSource;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import vn.svframe.svframelib.util.configobject.MapConfigObject;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
/** Native Fabric implementation of the SVFrameLib 1.7.1 skill/script registry. */
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
public SkillHandler<?> loadSkillHandler(String fallbackSkillHandlerId, Object input) {
Objects.requireNonNull(input, "Input cannot be null");
if (input instanceof String value) {
String text = value.trim();
int colon = text.indexOf(':');
if (colon >= 0) {
String sourceKey = text.substring(0, colon);
SkillHandlerSource source = skillHandlerSources.get(norm(sourceKey));
if (source == null) throw new IllegalArgumentException("Could not find skill source '" + sourceKey + "'");
String internal = text.substring(colon + 1);
return source.getConstructor().apply(new MapConfigObject(UNIDENTIFIED_SCRIPT_ID, Map.of("source", text)), internal);
}
return getHandlerOrThrow(enumName(text));
}
if (input instanceof ConfigObject config) {
String sourceText = config.contains("source") ? config.getString("source") : null;
if (sourceText == null) {
SkillHandler<?> legacy = findLegacySkillSource(config);
if (legacy != null) return legacy;
throw new IllegalArgumentException("Could not find skill source");
}
return sourceHandler(config, sourceText);
}
if (input instanceof Map<?, ?> raw) return loadSkillHandler(fallbackSkillHandlerId, mapObject(fallbackSkillHandlerId, raw));
if (input instanceof List<?> list) return new SVFrameLibSkillHandler(loadScript(fallbackSkillHandlerId, list));
throw new IllegalArgumentException("Provide either a string or configuration section instead of " + input.getClass().getSimpleName());
}
private SkillHandler<?> findLegacySkillSource(ConfigObject config) {
for (SkillHandlerSource source : skillHandlerSources.values())
for (String legacyPath : source.getLegacyInternalSkillPaths())
if (config.contains(legacyPath)) {
String skillId = config.getString(legacyPath);
if (skillId != null) return source.getConstructor().apply(config, skillId);
}
return null;
}
private SkillHandler<?> sourceHandler(ConfigObject config, String sourceText) {
int colon = sourceText.indexOf(':');
if (colon < 0) throw new IllegalArgumentException("Source must be in the format 'source:InternalSkillName'");
String sourceKey = sourceText.substring(0, colon);
SkillHandlerSource source = skillHandlerSources.get(norm(sourceKey));
if (source == null) throw new IllegalArgumentException("Could not find skill source '" + sourceKey + "'");
return source.getConstructor().apply(config, sourceText.substring(colon + 1));
}
public SkillHandler<?> getHandlerOrThrow(String id) { return Objects.requireNonNull(getHandler(id), "Could not find skill handler with ID '" + id + "'"); }
public SkillHandler<?> getHandler(String id) { return handlers.get(norm(id)); }
public Collection<SkillHandler<?>> getHandlers() { return List.copyOf(handlers.values()); }
public Script loadScript(Object input) { return loadScript(UNIDENTIFIED_SCRIPT_ID, input); }
public Script loadScript(String id, Object input) {
Objects.requireNonNull(input, "Input cannot be null");
if (input instanceof String scriptId) return getScriptOrThrow(scriptId);
if (input instanceof ConfigObject config) {
Script script = new Script(config);
script.getPostLoadAction().performAction();
return script;
}
if (input instanceof Map<?, ?> raw) return loadScript(id, mapObject(id, raw));
if (input instanceof List<?> list) {
if (id == null) throw new IllegalArgumentException("Cannot use unidentified script here");
List<String> mechanicLines = new ArrayList<>(list.size());
for (Object value : list) mechanicLines.add(String.valueOf(value));
Script script = new Script(id, mechanicLines);
script.getPostLoadAction().performAction();
return script;
}
throw new IllegalArgumentException("Expected a string, config section or list");
}
public Script getScriptOrThrow(String id) { return Objects.requireNonNull(getScript(id), "Could not find script with ID '" + id + "'"); }
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
if (handlers.putIfAbsent(key, handler) != null) throw new IllegalArgumentException("A skill handler with ID '" + handler.getId() + "' already exists");
}
public void registerScript(Script script) {
Objects.requireNonNull(script, "script");
String key = norm(script.getId());
if (scripts.putIfAbsent(key, script) != null) throw new IllegalArgumentException("A script with ID '" + script.getId() + "' already exists");
}
public void registerCondition(String id, Function<ConfigObject, Condition> factory, String... aliases) { ensureRegistration("Condition"); putAliases(conditions, id, factory, aliases); }
public void registerMechanic(String id, Function<ConfigObject, Mechanic> factory, String... aliases) { ensureRegistration("Mechanic"); putAliases(mechanics, id, factory, aliases); }
public void registerEntityTargeter(String id, Function<ConfigObject, EntityTargeter> factory, String... aliases) { ensureRegistration("Targeter"); putAliases(entityTargets, id, factory, aliases); }
public void registerLocationTargeter(String id, Function<ConfigObject, LocationTargeter> factory, String... aliases) { ensureRegistration("Targeter"); putAliases(locationTargets, id, factory, aliases); }
private void ensureRegistration(String objectType) {
if (!registration) throw new IllegalStateException(objectType + " registration is disabled");
}
public void registerBuiltinSkillHandlerSource(Class<? extends SkillHandler<?>> type) {
Objects.requireNonNull(type, "Skill class cannot be null");
if (type.getAnnotation(BuiltinSkillHandler.class) == null)
throw new IllegalArgumentException("No BuiltinSkillHandler annotation on class " + type.getName());
builtInSkillHandlerTypes.put(enumName(type.getSimpleName()), type);
}
@Deprecated
public void registerBuiltinSkillHandlerType(Class<? extends SkillHandler<?>> type) { registerBuiltinSkillHandlerSource(type); }
@Override public synchronized void initialize(boolean clear) {
if (clear) {
handlers.clear(); scripts.clear(); mechanics.clear(); conditions.clear(); entityTargets.clear(); locationTargets.clear(); builtInSkillHandlerTypes.clear(); skillHandlerSources.clear();
registerNativeBuiltins();
}
registration = false;
}
public synchronized void reload() { handlers.clear(); scripts.clear(); registration = true; }
public boolean isRegistrationOpen() { return registration; }
private void registerNativeBuiltins() {
registerCondition("boolean", BooleanCondition::new);
registerCondition("compare", CompareCondition::new);
registerCondition("has_variable", HasVariableCondition::new);
registerCondition("in_between", InBetweenCondition::new);
registerCondition("string_contains", StringContainsCondition::new);
registerCondition("string_equals", StringEqualsCondition::new);
registerCondition("biome", BiomeCondition::new);
registerCondition("cuboid", CuboidCondition::new);
registerCondition("distance", DistanceCondition::new);
registerCondition("world", WorldCondition::new);
registerCondition("can_target", CanTargetCondition::new, "can_tgt", "cantarget", "ctgt");
registerCondition("cooldown", CooldownCondition::new);
registerCondition("food", FoodCondition::new);
registerCondition("has_ammo", HasAmmoCondition::new, "ammo");
registerCondition("has_damage_type", HasDamageTypeCondition::new);
registerCondition("is_living", IsLivingCondition::new);
registerCondition("on_fire", OnFireCondition::new);
registerCondition("permission", PermissionCondition::new);
registerCondition("random_chance", RandomChanceCondition::new);
registerCondition("time", TimeCondition::new);
registerMechanic("add_stat", AddStatModifierMechanic::new, "add_stat_modifier");
registerMechanic("remove_stat", RemoveStatModifierMechanic::new, "remove_stat_modifier");
registerMechanic("feed", FeedMechanic::new);
registerMechanic("heal", HealMechanic::new);
registerMechanic("reduce_cooldown", ReduceCooldownMechanic::new, "reduce_cd", "decrease_cooldown", "decrease_cd");
registerMechanic("saturate", SaturateMechanic::new);
registerMechanic("apply_cooldown", ApplyCooldownMechanic::new, "apply_cd");
registerMechanic("consume_ammo", ConsumeAmmoMechanic::new, "take_ammo");
registerMechanic("delay", DelayMechanic::new);
registerMechanic("dispatch_command", DispatchCommandMechanic::new, "c", "dispatch_cmd", "cmd", "command", "execute_command", "execute_cmd", "run_command", "run_cmd");
registerMechanic("entity_effect", EntityEffectMechanic::new);
registerMechanic("lightning", LightningStrikeMechanic::new, "lightning_strike");
registerMechanic("script", ScriptMechanic::new, "skill", "cast");
registerMechanic("teleport", TeleportMechanic::new, "tp", "set_position", "set_pos", "setpos", "setposition", "set_location", "setlocation", "set_loc", "setloc", "move", "moveto", "move_to");
registerMechanic("set_velocity", VelocityMechanic::new, "velocity", "setvel", "set_vel", "setvelocity");
registerMechanic("additive_damage_buff", AdditiveDamageBuffMechanic::new);
registerMechanic("damage", DamageMechanic::new, "deal_damage", "dmg", "deal_dmg", "dealdamage", "dealdmg", "attack", "atk");
registerMechanic("multiply_damage", MultiplyDamageMechanic::new);
registerMechanic("potion", PotionMechanic::new);
registerMechanic("remove_potion", RemovePotionMechanic::new);
registerMechanic("set_on_fire", SetFireMechanic::new);
registerMechanic("set_no_damage_ticks", SetNoDamageTicksMechanic::new);
registerMechanic("mark_crit", MarkCritMechanic::new);
registerMechanic("give_item", GiveItemMechanic::new);
registerRawMechanic("sudo");
registerMechanic("kick", KickMechanic::new);
registerMechanic("close_inventory", CloseInventoryMechanic::new);
registerMechanic("go_back", GoBackMechanic::new);
registerMechanic("call_trigger", CallTriggerMechanic::new);
registerMechanic("cancel_event", CancelEventMechanic::new);
registerMechanic("shoot_arrow", ShootArrowMechanic::new, "fire_arrow", "bowshoot", "bow_shoot", "shoot_bow");
registerMechanic("shulker_bullet", ShulkerBulletMechanic::new);
registerRawMechanic("raytrace_blocks");
registerRawMechanic("raytrace_entities");
registerMechanic("draw_helix", HelixMechanic::new, "helix");
registerRawMechanic("draw_line", "line");
registerRawMechanic("draw_parabola", "parabola", "spawn_parabola");
registerMechanic("projectile", ProjectileMechanic::new);
registerMechanic("ray_trace", RaytraceMechanic::new, "raytrace", "cast_ray", "ray_cast", "raycast");
registerRawMechanic("slash");
registerRawMechanic("draw_sphere", "sphere");
registerRawMechanic("add_vector", "add_vec"); registerRawMechanic("cross_product"); registerRawMechanic("dot_product"); registerRawMechanic("hadamard_product"); registerRawMechanic("multiply_vector"); registerRawMechanic("normalize_vector", "normalize"); registerRawMechanic("orient_vector", "orient_vec"); registerRawMechanic("save_vector", "copy_vector", "save_vec", "copy_vec"); registerRawMechanic("set_x"); registerRawMechanic("set_y"); registerRawMechanic("set_z"); registerRawMechanic("subtract_vector", "sub_vec", "sub_vector", "subvec");
registerRawMechanic("increment", "incr"); registerRawMechanic("set_boolean", "set_bool"); registerRawMechanic("set_double", "set_float"); registerRawMechanic("set_integer", "set_int"); registerRawMechanic("set_string", "set_str"); registerRawMechanic("set_vector", "set_vec");
registerMechanic("action_bar", ActionBarMechanic::new);
registerMechanic("particle", ParticleMechanic::new, "spawn_particle", "par");
registerMechanic("sound", SoundMechanic::new, "play_world_sound", "play_sound", "world_sound");
registerRawMechanic("player_sound", "play_player_sound");
registerMechanic("tell", MessageMechanic::new, "message", "msg", "send", "send_message", "send_msg");
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
putAliases(mechanics, id, factory, aliases);
}
private static <T> void putAliases(Map<String, T> map, String id, T value, String... aliases) {
Objects.requireNonNull(id, "id"); Objects.requireNonNull(value, "factory");
if (map.putIfAbsent(norm(id), value) != null) throw new IllegalArgumentException("Duplicate registry ID '" + id + "'");
if (aliases != null) for (String alias : aliases) if (alias != null && !alias.isBlank())
if (map.putIfAbsent(norm(alias), value) != null) throw new IllegalArgumentException("Duplicate registry ID '" + alias + "'");
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
if (!config.contains(key)) return List.of();
String raw = config.getString(key, "").trim();
if (raw.isEmpty()) return List.of();
if (raw.indexOf('\n') >= 0) return raw.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
return List.of(raw);
}
private static MapConfigObject mapObject(String key, Map<?, ?> raw) {
Map<String, Object> map = new LinkedHashMap<>();
raw.forEach((k, v) -> map.put(String.valueOf(k), v));
return new MapConfigObject(key, map);
}
private static String render(String id, ConfigObject config) {
StringBuilder out = new StringBuilder(id == null ? "" : id);
if (config == null || config.getKeys().isEmpty()) return out.toString();
out.append('{'); boolean first = true;
for (String key : config.getKeys()) {
if ("type".equals(key)) continue;
if (!first) out.append(';'); first = false;
out.append(key).append('=').append(config.getString(key, ""));
}
return out.append('}').toString();
}
private static String norm(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
private static String enumName(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
}
