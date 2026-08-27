package vn.svframe.svframelib.fabric.runtime.script;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Native execution engine for SVFrameLib 1.7.1 script lines. */
public final class ScriptEngine {
    public record Definition(String id, boolean isPublic, List<String> conditions, List<String> mechanics) {
        public Definition {
            id = id.toLowerCase(Locale.ROOT);
            conditions = List.copyOf(conditions);
            mechanics = List.copyOf(mechanics);
        }
    }

    private static final Set<String> CONDITIONS = Set.of(
            "boolean", "compare", "has_variable", "in_between", "string_contains", "string_equals",
            "biome", "cuboid", "distance", "world", "can_target", "cooldown", "food", "has_ammo",
            "has_damage_type", "is_living", "on_fire", "permission", "random_chance", "time");

    private static final Set<String> MECHANICS = Set.of(
            "add_stat_modifier", "remove_stat_modifier", "feed", "heal", "reduce_cooldown", "saturate",
            "apply_cooldown", "consume_ammo", "delay", "dispatch_command", "entity_effect", "lightning",
            "script", "teleport", "set_velocity", "additive_damage_buff", "damage", "multiply_damage",
            "potion", "remove_potion", "set_on_fire", "set_no_damage_ticks", "mark_crit", "give_item",
            "sudo", "kick", "close_inventory", "go_back", "call_trigger", "cancel_event", "shoot_arrow",
            "shulker_bullet", "raytrace_blocks", "raytrace_entities", "draw_helix", "draw_line",
            "draw_parabola", "projectile", "ray_trace", "slash", "draw_sphere", "add_vector",
            "cross_product", "dot_product", "hadamard_product", "multiply_vector", "normalize_vector",
            "orient_vector", "save_vector", "set_x", "set_y", "set_z", "subtract_vector", "increment",
            "set_boolean", "set_double", "set_integer", "set_string", "set_vector", "action_bar", "particle",
            "sound", "player_sound", "tell");

    private final Map<String, Definition> defs = new HashMap<>();
    private final ExpressionRuntime expressions = new ExpressionRuntime();
    private final ScriptPlatform platform;

    public ScriptEngine(ScriptPlatform platform) { this.platform = platform; }
    public void register(Definition definition) { defs.put(definition.id(), definition); }
    public Optional<Definition> find(String id) { return Optional.ofNullable(defs.get(norm(id))); }
    public boolean cast(String id, ScriptContext context) { return cast(id, context, 0); }
    public boolean cast(Definition definition, ScriptContext context) { return castDefinition(definition, context, 0); }
    public static boolean supportsCondition(String id) { return CONDITIONS.contains(canonicalCondition(id)); }
    public static boolean supportsMechanic(String id) { return MECHANICS.contains(canonicalMechanic(id)); }

    private boolean cast(String id, ScriptContext context, int depth) {
        if (depth > 32) throw new IllegalStateException("script recursion");
        Definition definition = defs.get(norm(id));
        return definition != null && castDefinition(definition, context, depth);
    }

    private boolean castDefinition(Definition definition, ScriptContext context, int depth) {
        if (depth > 32) throw new IllegalStateException("script recursion");
        for (String raw : definition.conditions()) if (!condition(ScriptLineParser.parse(raw), context)) return false;
        runMechanics(definition.mechanics(), 0, context, depth);
        return true;
    }

    /** Matches the original MechanicQueue: delay schedules the remaining queue and returns immediately. */
    private void runMechanics(List<String> mechanics, int start, ScriptContext context, int depth) {
        for (int index = start; index < mechanics.size(); index++) {
            if (context.cancelled()) return;
            ScriptLineParser.Call call = ScriptLineParser.parse(mechanics.get(index));
            if (canonicalMechanic(call.name()).equals("delay")) {
                int ticks = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (long) number(call, "amount", 0, context)));
                int next = index + 1;
                platform.delay(ticks, () -> runMechanics(mechanics, next, context, depth));
                return;
            }
            mechanic(call, context, depth);
        }
    }

    private boolean condition(ScriptLineParser.Call rawCall, ScriptContext context) {
        ScriptLineParser.Call call = canonical(rawCall, canonicalCondition(rawCall.name()));
        Map<String, String> params = call.params();
        return switch (call.name()) {
            case "boolean" -> expressions.evaluateBoolean(context.resolve(first(params, "0", "formula", "form", "f", "expression", "expr", "e")), numericVariables(context));
            case "compare" -> compare(number(call, "first", 0, context), number(call, "second", 0, context), first(params, "EQUALS", "comparator"));
            case "has_variable" -> context.hasVariable(first(params, "", "variable", "var", "v", "name", "n"));
            case "in_between" -> {
                double first = number(call, "first", 0, context), second = number(call, "second", 0, context), third = number(call, "third", 0, context);
                yield first <= second && second < third;
            }
            case "string_contains" -> {
                String search = context.resolve(first(params, "", "search", "look", "lookfor", "lf"));
                String within = context.resolve(first(params, "", "in", "within"));
                boolean ignoreCase = boolAny(call, false, "ignore_case", "ic");
                yield ignoreCase ? search.toLowerCase(Locale.ROOT).contains(within.toLowerCase(Locale.ROOT)) : search.contains(within);
            }
            case "string_equals" -> {
                String first = context.resolve(first(params, "", "first", "left", "lhs"));
                String second = context.resolve(first(params, "", "second", "right", "rhs"));
                yield boolAny(call, false, "ignore_case", "ic") ? first.equalsIgnoreCase(second) : first.equals(second);
            }
            case "biome" -> {
                Vector3 at = conditionLocation(call, context, true);
                String actual = registryPath(platform.biome(context.caster(), at));
                yield Arrays.stream(params.getOrDefault("name", "").split(",")).map(String::trim).map(ScriptEngine::registryPath).anyMatch(actual::equalsIgnoreCase);
            }
            case "cuboid" -> {
                Vector3 one = requireVector(context, params.getOrDefault("loc1", ""));
                Vector3 two = requireVector(context, params.getOrDefault("loc2", ""));
                Vector3 at = conditionLocation(call, context, false);
                yield between(at.x(), one.x(), two.x()) && between(at.y(), one.y(), two.y()) && between(at.z(), one.z(), two.z());
            }
            case "distance" -> {
                Vector3 at = conditionLocation(call, context, true);
                Vector3 center = resolveLocation(params.getOrDefault("location", "target_location"), context);
                yield at.subtract(center).length() <= number(call, "max", 0, context);
            }
            case "world" -> registryPath(platform.worldName(context.caster())).equalsIgnoreCase(registryPath(params.getOrDefault("name", "")));
            case "can_target" -> platform.canTarget(context.caster(), context.target(), first(params, "OFFENSE_ACTION", "interaction_type", "it", "type"));
            case "cooldown" -> platform.cooldownReady(context.caster(), params.getOrDefault("path", ""));
            case "food" -> platform.foodLevel(context.caster()) >= (int) number(call, "amount", 0, context);
            case "has_ammo" -> ammoCondition(call, context);
            case "has_damage_type" -> {
                String types = first(params, "", "types", "damage_types", "damage_type", "dtype", "dt");
                yield Arrays.stream(types.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).map(ScriptEngine::enumName).anyMatch(context.damageTypes()::contains);
            }
            case "is_living" -> platform.isLiving(boolAny(call, false, "caster") ? context.caster() : target(params, context));
            case "on_fire" -> platform.isOnFire(boolAny(call, false, "caster") ? context.caster() : target(params, context));
            case "permission" -> platform.hasPermission(context.caster(), params.getOrDefault("name", ""));
            case "random_chance" -> Math.random() < numberAny(call, 0, context, "chance", "c", "percentage", "percent", "p");
            case "time" -> timePeriod(first(params, "", "period"), platform.worldTime(context.caster()));
            default -> throw new IllegalArgumentException("Unknown script condition: " + call.name());
        };
    }

    private void mechanic(ScriptLineParser.Call rawCall, ScriptContext context, int depth) {
        ScriptLineParser.Call call = canonical(rawCall, canonicalMechanic(rawCall.name()));
        Map<String, String> params = call.params();
        switch (call.name()) {
            case "add_stat_modifier" -> platform.addStat(target(params, context), require(params, "stat"), first(params, "default", "key", "k"), numberAny(call, 0, context, "amount", "a", "value", "v"), boolAny(call, false, "relative"), boolAny(call, false, "unique", "u"), Math.max(0L, (long) numberAny(call, 0, context, "time", "duration", "dur", "d", "ticks", "t")));
            case "remove_stat_modifier" -> platform.removeStat(target(params, context), require(params, "stat"), require(params, "key"));
            case "feed" -> platform.setFoodLevel(target(params, context), (int) number(call, "amount", 0, context));
            case "heal" -> platform.heal(target(params, context), numberAny(call, 0, context, "amount", "amt", "a", "value", "val", "v", "health", "hp"));
            case "reduce_cooldown" -> platform.reduceCooldown(context.caster(), require(params, "path"), params.getOrDefault("reduction", "FLAT"), numberAny(call, 0, context, "value", "val", "v"));
            case "saturate" -> platform.setSaturation(target(params, context), (float) number(call, "amount", 0, context));
            case "apply_cooldown" -> platform.applyCooldown(context.caster(), firstRequired(params, "path", "p", "id", "name"), numberAny(call, 0, context, "time", "t", "value", "val", "v", "amount", "amt", "a", "cooldown", "cd"));
            case "consume_ammo" -> consumeAmmo(call, context);
            case "delay" -> throw new IllegalStateException("Delay is handled by the mechanic queue");
            case "dispatch_command" -> platform.dispatchCommand(target(params, context), context.resolve(firstRequired(params, "format", "fmt", "f", "command", "cmd", "c")), boolAny(call, true, "from_console", "console", "s", "server", "from_server"), boolAny(call, false, "op", "operator"));
            case "entity_effect" -> platform.entityEffect(target(params, context), params.getOrDefault("effect", "HURT"));
            case "lightning" -> platform.lightning(context.caster(), resolveTargetLocation(params, context), boolAny(call, false, "effect"));
            case "script" -> castNested(call, context, depth);
            case "teleport" -> {
                Vector3 location = params.containsKey("target_location") ? resolveLocation(params.get("target_location"), context) : resolveTargetLocation(params, context);
                platform.teleport(target(params, context), location.add(new Vector3(0, number(call, "y_offset", 0, context), 0)));
            }
            case "set_velocity" -> platform.velocity(target(params, context), requireVector(context, firstRequired(params, "value", "val", "v", "vector", "vec", "velocity", "vel")));
            case "additive_damage_buff" -> additiveDamage(call, context);
            case "damage" -> platform.damage(target(params, context), number(call, "amount", context.damage(), context), first(params, "", "damage_type", "dtype", "dt"));
            case "multiply_damage" -> multiplyDamage(call, context);
            case "potion" -> platform.potion(target(params, context), first(params, "SLOW", "effect", "eff", "e", "type", "pe"), (int) numberAny(call, 1, context, "level", "lvl", "l"), (int) numberAny(call, 20, context, "ticks", "t", "duration", "dur", "d", "time"), boolAny(call, false, "ambient", "amb"), boolAny(call, true, "particles", "part"), boolAny(call, true, "icon", "ic"));
            case "remove_potion" -> platform.removePotion(target(params, context), firstRequired(params, "effect", "type"));
            case "set_on_fire" -> platform.setOnFire(target(params, context), (int) numberAny(call, 20, context, "ticks", "time", "duration", "d", "t"));
            case "set_no_damage_ticks" -> platform.noDamageTicks(target(params, context), (int) numberAny(call, 10, context, "ticks", "t", "duration", "dur", "d", "time"), boolAny(call, false, "stack", "add"), boolAny(call, false, "min"), boolAny(call, false, "max"));
            case "mark_crit" -> markCrit(params, context);
            case "give_item" -> platform.giveItem(target(params, context), firstRequired(params, "material", "mat", "m"), Math.max(1, (int) numberAny(call, 1, context, "amount", "amt", "a", "count", "cnt", "c", "number", "num", "nb", "n")));
            case "sudo" -> platform.dispatchCommand(target(params, context), context.resolve(firstRequired(params, "format", "fmt", "command", "cmd", "c", "f")), false, false);
            case "kick" -> platform.kick(target(params, context), context.resolve(params.getOrDefault("message", "You were kicked")));
            case "close_inventory", "go_back" -> platform.closeInventory(context.caster());
            case "call_trigger" -> platform.trigger(context.caster(), firstRequired(params, "trigger", "name", "id"), context);
            case "cancel_event" -> context.cancel();
            case "shoot_arrow" -> platform.shootArrow(context.caster(), numberAny(call, 1, context, "velocity", "vel", "speed", "sp"), number(call, "damage", context.damage(), context));
            case "shulker_bullet" -> platform.shulkerBullet(context.caster(), context.target(), number(call, "damage", context.damage(), context));
            case "raytrace_blocks" -> raytraceBlocks(call, context, depth);
            case "raytrace_entities", "ray_trace", "projectile" -> projectile(call, context, depth);
            case "draw_helix" -> helix(call, context, depth);
            case "draw_line" -> line(call, context, depth);
            case "draw_parabola" -> parabola(call, context, depth);
            case "slash" -> slash(call, context, depth);
            case "draw_sphere" -> sphere(call, context, depth);
            case "add_vector" -> addVector(call, context);
            case "cross_product" -> binaryVector(call, context, "cross");
            case "dot_product" -> binaryVector(call, context, "dot");
            case "hadamard_product" -> binaryVector(call, context, "hadamard");
            case "multiply_vector" -> multiplyVector(call, context);
            case "normalize_vector" -> mutateVector(call, context, "normalize");
            case "orient_vector" -> orientVector(call, context);
            case "save_vector" -> saveVector(call, context);
            case "set_x" -> setCoordinate(call, context, 'x');
            case "set_y" -> setCoordinate(call, context, 'y');
            case "set_z" -> setCoordinate(call, context, 'z');
            case "subtract_vector" -> subtractVector(call, context);
            case "increment" -> increment(call, context);
            case "set_boolean" -> setBoolean(call, context);
            case "set_double" -> setDouble(call, context);
            case "set_integer" -> setInteger(call, context);
            case "set_string" -> setString(call, context);
            case "set_vector" -> setVector(call, context);
            case "action_bar" -> platform.actionBar(target(params, context), context.resolve(first(params, "", "m", "message")), (int) number(call, "priority", 0, context), (int) number(call, "duration", 20, context));
            case "particle" -> particle(call, context);
            case "sound" -> platform.sound(target(params, context), firstRequired(params, "sound", "s"), (float) numberAny(call, 1, context, "volume", "vol", "v"), (float) numberAny(call, 1, context, "pitch", "p"));
            case "player_sound" -> platform.playerSound(target(params, context), firstRequired(params, "sound", "s"), (float) numberAny(call, 1, context, "volume", "vol", "v"), (float) numberAny(call, 1, context, "pitch", "p"));
            case "tell" -> platform.message(target(params, context), context.resolve(first(params, "", "message", "msg", "m", "format", "fmt", "f", "text", "txt")));
            default -> throw new IllegalArgumentException("Unknown script mechanic: " + call.name());
        }
    }

    private boolean ammoCondition(ScriptLineParser.Call call, ScriptContext context) {
        Map<String, String> params = call.params();
        if (boolAny(call, false, "creative_infinite") && platform.isCreative(context.caster())) return true;
        String ignore = params.get("item_ignore_tag");
        if (ignore != null && !ignore.isBlank() && platform.heldBooleanTag(context.caster(), ignore)) return true;
        String item = first(params, "minecraft:arrow", "item", "material");
        boolean found = platform.hasItem(context.caster(), item, 1);
        if (found && boolAny(call, false, "consume_if_met")) platform.takeItem(context.caster(), item, 1);
        return found;
    }

    private void consumeAmmo(ScriptLineParser.Call call, ScriptContext context) {
        Map<String, String> params = call.params();
        if (boolAny(call, false, "creative_infinite") && platform.isCreative(context.caster())) return;
        String ignore = params.get("item_ignore_tag");
        if (ignore != null && !ignore.isBlank() && platform.heldBooleanTag(context.caster(), ignore)) return;
        platform.takeItem(context.caster(), first(params, "minecraft:arrow", "item", "material"), 1);
    }

    private void additiveDamage(ScriptLineParser.Call call, ScriptContext context) {
        double amount = number(call, "amount", 0, context);
        String type = first(call.params(), "", "damage_type", "dtype", "dt").trim();
        if (context.damageBridge() == null) return;
        if (type.isEmpty()) context.damageBridge().additiveAll(amount); else context.damageBridge().additiveType(type, amount);
    }

    private void multiplyDamage(ScriptLineParser.Call call, ScriptContext context) {
        double amount = numberAny(call, 1, context, "value", "val", "v", "amount", "amt", "a", "scalar", "s", "coef", "c");
        String type = first(call.params(), "", "damage_type", "dtype", "dt").trim();
        String element = call.params().getOrDefault("element", "").trim();
        boolean additive = boolAny(call, false, "additive");
        if (context.damageBridge() == null) { if (!additive) context.damage(context.damage() * amount); return; }
        if (additive) {
            if (!type.isEmpty()) context.damageBridge().additiveType(type, amount); else context.damageBridge().additiveAll(amount);
        } else {
            if (!element.isEmpty()) context.damageBridge().multiplyElement(element, amount);
            else if (!type.isEmpty()) context.damageBridge().multiplyType(type, amount);
            else context.damageBridge().multiplyAll(amount);
        }
    }

    private void markCrit(Map<String, String> params, ScriptContext context) {
        context.objects().put("critical", true);
        String raw = first(params, "", "damage_types", "damage_type", "dtypes", "dtype", "dt");
        for (String type : raw.split("[,;]")) if (!type.isBlank()) context.damageTypes().add("CRIT_" + enumName(type));
    }

    private void castNested(ScriptLineParser.Call call, ScriptContext context, int depth) {
        Map<String, String> params = call.params();
        String name = first(params, "", "name", "script", "skill", "s");
        int iterations = Math.max(1, (int) numberAny(call, 1, context, "iterations", "iteration", "times", "amount"));
        String counter = params.get("counter");
        for (int i = 1; i <= iterations; i++) {
            if (counter != null) context.setVariable("SKILL", counter, i);
            cast(name, context, depth + 1);
        }
    }

    private void particle(ScriptLineParser.Call call, ScriptContext context) {
        Map<String, String> params = call.params();
        String particle = first(params, "CRIT", "particle", "p", "type");
        int amount = (int) numberAny(call, 1, context, "amount", "count", "a", "c");
        double dx = numberAny(call, 0, context, "x", "offset_x", "ox"), dy = numberAny(call, 0, context, "y", "offset_y", "oy"), dz = numberAny(call, 0, context, "z", "offset_z", "oz"), speed = numberAny(call, 0, context, "speed", "s");
        if (context.targetLocation() != null) platform.particleAt(context.targetLocation(), particle, amount, dx, dy, dz, speed);
        else platform.particle(target(params, context), particle, amount, dx, dy, dz, speed);
    }

    private void projectile(ScriptLineParser.Call call, ScriptContext context, int depth) {
        Map<String, String> params = call.params();
        Vector3 origin = context.sourceLocation() != null ? context.sourceLocation() : platform.location(context.caster());
        Vector3 direction = platform.eyeDirection(context.caster()).normalize();
        if (params.containsKey("direction")) {
            Vector3 configured = context.vectorVariable(params.get("direction"));
            if (configured != null) direction = configured.normalize();
        }
        double speed = numberAny(call, 2, context, "speed", "velocity"), range = number(call, "range", number(call, "life_span", 20, context) * speed / 20d, context), size = numberAny(call, .2, context, "size", "hitbox");
        int life = (int) numberAny(call, 20, context, "life_span", "lifespan", "duration");
        String tick = first(params, "", "tick", "on_tick"), hit = first(params, "", "hit_entity", "on_hit", "hit"), end = first(params, "", "end", "hit_block", "on_end");
        platform.projectile(new ScriptPlatform.ProjectileSpec(origin, direction, speed, range, size, life), location -> {
            if (!tick.isBlank()) { ScriptContext nested = context.copy(); nested.targetLocation(location); cast(tick, nested, depth + 1); }
        }, entity -> {
            if (!hit.isBlank()) { ScriptContext nested = context.copy(); nested.target(entity); cast(hit, nested, depth + 1); }
        }, () -> { if (!end.isBlank()) cast(end, context.copy(), depth + 1); });
    }

    private void raytraceBlocks(ScriptLineParser.Call call, ScriptContext context, int depth) {
        Map<String, String> params = call.params();
        double range = number(call, "range", 50, context), step = Math.max(.01, number(call, "step", .4, context));
        int life = Math.max(1, (int) Math.ceil(range / step));
        String tick = params.getOrDefault("tick", ""), hit = params.getOrDefault("hit_block", "");
        Vector3 origin = context.sourceLocation() != null ? context.sourceLocation() : platform.location(context.caster());
        platform.projectile(new ScriptPlatform.ProjectileSpec(origin, platform.eyeDirection(context.caster()).normalize(), step, range, .01, life), location -> {
            if (!tick.isBlank()) { ScriptContext nested = context.copy(); nested.targetLocation(location); cast(tick, nested, depth + 1); }
        }, entity -> { }, () -> { if (!hit.isBlank()) cast(hit, context.copy(), depth + 1); });
    }

    private void helix(ScriptLineParser.Call call, ScriptContext context, int depth) {
        double radius = number(call, "radius", 1, context), height = number(call, "height", 2, context);
        int points = Math.max(1, (int) number(call, "points", 48, context));
        String tick = first(call.params(), "", "tick", "script");
        Vector3 base = resolveTargetLocation(call.params(), context);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points, y = height * i / points;
            runAt(tick, context, base.add(new Vector3(Math.cos(angle) * radius, y, Math.sin(angle) * radius)), depth);
        }
    }

    private void sphere(ScriptLineParser.Call call, ScriptContext context, int depth) {
        double radius = number(call, "radius", 1, context);
        int points = Math.max(1, (int) number(call, "points", 64, context));
        String tick = first(call.params(), "", "tick", "script");
        Vector3 base = resolveTargetLocation(call.params(), context);
        for (int i = 0; i < points; i++) {
            double phi = Math.acos(1 - 2 * (i + .5) / points), theta = Math.PI * (1 + Math.sqrt(5)) * i;
            runAt(tick, context, base.add(new Vector3(Math.cos(theta) * Math.sin(phi) * radius, Math.cos(phi) * radius, Math.sin(theta) * Math.sin(phi) * radius)), depth);
        }
    }

    private void line(ScriptLineParser.Call call, ScriptContext context, int depth) {
        Vector3 from = context.sourceLocation() != null ? context.sourceLocation() : platform.location(context.caster());
        Vector3 to = resolveTargetLocation(call.params(), context);
        String tick = first(call.params(), "", "tick", "script");
        int points = Math.max(1, (int) number(call, "points", 20, context));
        for (int i = 0; i <= points; i++) {
            double ratio = i / (double) points;
            runAt(tick, context, from.multiply(1 - ratio).add(to.multiply(ratio)), depth);
        }
    }

    private void parabola(ScriptLineParser.Call call, ScriptContext context, int depth) {
        Vector3 from = context.sourceLocation() != null ? context.sourceLocation() : platform.location(context.caster());
        Vector3 to = resolveTargetLocation(call.params(), context);
        double height = number(call, "height", 0, context), speed = Math.max(.01, number(call, "speed", 1, context));
        int points = Math.max(2, (int) Math.ceil(from.subtract(to).length() / speed));
        String start = call.params().getOrDefault("start", ""), tick = first(call.params(), "", "tick", "script"), end = call.params().getOrDefault("end", "");
        if (!start.isBlank()) runAt(start, context, from, depth);
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            Vector3 base = from.multiply(1 - t).add(to.multiply(t));
            runAt(tick, context, base.add(new Vector3(0, 4d * height * t * (1d - t), 0)), depth);
        }
        if (!end.isBlank()) runAt(end, context, to, depth);
    }

    private void slash(ScriptLineParser.Call call, ScriptContext context, int depth) {
        Vector3 origin = resolveTargetLocation(call.params(), context);
        Vector3 forward = platform.eyeDirection(context.caster()).normalize();
        double length = number(call, "length", 4, context), distance = number(call, "distance", 1, context);
        int points = Math.max(2, (int) number(call, "points", 20, context));
        String tick = first(call.params(), "", "tick", "script");
        Vector3 side = new Vector3(-forward.z(), 0, forward.x()).normalize();
        for (int i = 0; i <= points; i++) {
            double u = i / (double) points, centered = (u - .5) * length;
            double arc = Math.sqrt(Math.max(0, 1 - Math.pow(Math.abs(2 * u - 1), 2))) * distance;
            runAt(tick, context, origin.add(side.multiply(centered)).add(forward.multiply(arc)), depth);
        }
    }

    private void runAt(String script, ScriptContext context, Vector3 location, int depth) {
        if (script == null || script.isBlank()) return;
        ScriptContext nested = context.copy();
        nested.targetLocation(location);
        cast(script, nested, depth + 1);
    }

    private void setBoolean(ScriptLineParser.Call call, ScriptContext context) { context.setVariable(scope(call), variableName(call), expressions.evaluateBoolean(context.resolve(require(call.params(), "value")), numericVariables(context))); }
    private void setDouble(ScriptLineParser.Call call, ScriptContext context) { context.setVariable(scope(call), variableName(call), numberAny(call, 0, context, "value", "val", "double", "float", "rhs")); }
    private void setInteger(ScriptLineParser.Call call, ScriptContext context) { context.setVariable(scope(call), variableName(call), (int) number(call, "value", 0, context)); }
    private void setString(ScriptLineParser.Call call, ScriptContext context) { context.setVariable(scope(call), variableName(call), context.resolve(require(call.params(), "value"))); }

    private void setVector(ScriptLineParser.Call call, ScriptContext context) {
        Vector3 vector = new Vector3(number(call, "x", 0, context), number(call, "y", 0, context), number(call, "z", 0, context));
        String name = variableName(call);
        context.vectors().put(name, vector);
        context.setVariable(scope(call), name, vector);
    }

    private void increment(ScriptLineParser.Call call, ScriptContext context) {
        String name = variableName(call);
        Object current = context.variable(name);
        if (!(current instanceof Number number)) throw new IllegalArgumentException("Variable '" + name + "' is not numeric");
        context.setVariable(scope(call), name, number.intValue() + 1);
    }

    private void addVector(ScriptLineParser.Call call, ScriptContext context) {
        String name = variableName(call);
        Vector3 current = requireVector(context, name);
        String otherName = first(call.params(), "", "added", "add", "other", "rhs", "value", "val", "v");
        Vector3 added = otherName.isBlank() ? new Vector3(number(call, "x", 0, context), number(call, "y", 0, context), number(call, "z", 0, context)) : requireVector(context, otherName);
        storeVector(context, call, name, current.add(added));
    }

    private void subtractVector(ScriptLineParser.Call call, ScriptContext context) {
        String name = variableName(call);
        Vector3 current = requireVector(context, name);
        String otherName = first(call.params(), "", "subtracted", "subtract", "sub", "other", "rhs", "value", "val", "v");
        Vector3 sub = otherName.isBlank() ? new Vector3(number(call, "x", 0, context), number(call, "y", 0, context), number(call, "z", 0, context)) : requireVector(context, otherName);
        storeVector(context, call, name, current.subtract(sub));
    }

    private void multiplyVector(ScriptLineParser.Call call, ScriptContext context) {
        String name = variableName(call);
        storeVector(context, call, name, requireVector(context, name).multiply(numberAny(call, 1, context, "value", "val", "v", "amount", "scalar", "coef", "c")));
    }

    private void mutateVector(ScriptLineParser.Call call, ScriptContext context, String op) {
        String name = variableName(call);
        Vector3 value = requireVector(context, name);
        storeVector(context, call, name, op.equals("normalize") ? value.normalize() : value);
    }

    private void orientVector(ScriptLineParser.Call call, ScriptContext context) {
        String name = variableName(call);
        storeVector(context, call, name, orient(requireVector(context, name), requireVector(context, require(call.params(), "axis"))));
    }

    private void saveVector(ScriptLineParser.Call call, ScriptContext context) {
        String name = variableName(call);
        storeVector(context, call, name, requireVector(context, firstRequired(call.params(), "value", "val", "v", "vector", "vec", "source", "from")));
    }

    private void setCoordinate(ScriptLineParser.Call call, ScriptContext context, char axis) {
        String name = variableName(call);
        Vector3 value = requireVector(context, name);
        double coordinate = numberAny(call, 0, context, "value", "val", "v", String.valueOf(axis));
        Vector3 changed = switch (axis) { case 'x' -> new Vector3(coordinate, value.y(), value.z()); case 'y' -> new Vector3(value.x(), coordinate, value.z()); default -> new Vector3(value.x(), value.y(), coordinate); };
        storeVector(context, call, name, changed);
    }

    private void binaryVector(ScriptLineParser.Call call, ScriptContext context, String op) {
        Vector3 a = requireVector(context, require(call.params(), "vec1")), b = requireVector(context, require(call.params(), "vec2"));
        String name = variableName(call);
        if (op.equals("dot")) { context.setVariable(scope(call), name, dot(a, b)); return; }
        Vector3 result = op.equals("cross") ? cross(a, b) : new Vector3(a.x() * b.x(), a.y() * b.y(), a.z() * b.z());
        storeVector(context, call, name, result);
    }

    private void storeVector(ScriptContext context, ScriptLineParser.Call call, String name, Vector3 value) {
        context.vectors().put(name, value);
        context.setVariable(scope(call), name, value);
    }

    private Vector3 conditionLocation(ScriptLineParser.Call call, ScriptContext context, boolean defaultSource) {
        boolean source = boolAny(call, defaultSource, "source");
        if (source) return context.sourceLocation() != null ? context.sourceLocation() : platform.location(context.caster());
        return resolveTargetLocation(call.params(), context);
    }

    private Vector3 resolveTargetLocation(Map<String, String> params, ScriptContext context) {
        if (params.containsKey("target_location")) return resolveLocation(params.get("target_location"), context);
        if (context.targetLocation() != null) return context.targetLocation();
        return context.target() != null ? platform.location(context.target()) : platform.location(context.caster());
    }

    private Vector3 resolveLocation(String value, ScriptContext context) {
        if (value == null || value.isBlank()) return resolveTargetLocation(Map.of(), context);
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "source", "source_location", "caster_location", "caster" -> context.sourceLocation() != null ? context.sourceLocation() : platform.location(context.caster());
            case "target", "target_location", "targetlocation" -> context.targetLocation() != null ? context.targetLocation() : platform.location(context.target());
            default -> {
                Vector3 variable = context.vectorVariable(value);
                if (variable == null) throw new IllegalArgumentException("Variable '" + value + "' is not a position");
                yield variable;
            }
        };
    }

    private Vector3 requireVector(ScriptContext context, String name) {
        Vector3 vector = vectorValue(name, context);
        if (vector == null) throw new IllegalArgumentException("Variable '" + name + "' is not a vector");
        return vector;
    }

    private Vector3 vectorValue(String value, ScriptContext context) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "caster.location" -> platform.location(context.caster());
            case "target.location" -> platform.location(context.target());
            case "source", "source.location" -> context.sourceLocation() != null ? context.sourceLocation() : platform.location(context.caster());
            case "target_location", "targetlocation" -> context.targetLocation() != null ? context.targetLocation() : platform.location(context.target());
            case "caster.eye_direction" -> platform.eyeDirection(context.caster());
            default -> context.vectorVariable(value);
        };
    }

    private UUID target(Map<String, String> params, ScriptContext context) {
        String target = params.getOrDefault("target", "").toLowerCase(Locale.ROOT);
        return target.equals("caster") || target.equals("source") ? context.caster() : context.target() != null ? context.target() : context.caster();
    }

    private double numberAny(ScriptLineParser.Call call, double fallback, ScriptContext context, String... keys) {
        for (String key : keys) if (call.params().containsKey(key)) return number(call, key, fallback, context);
        return fallback;
    }

    private double number(ScriptLineParser.Call call, String key, double fallback, ScriptContext context) {
        String expression = call == null ? null : call.params().get(key);
        if (expression == null) return fallback;
        String resolved = context.resolve(expression);
        try { return expressions.evaluate(resolved, numericVariables(context)); }
        catch (RuntimeException ignored) {
            try { return Double.parseDouble(resolved); }
            catch (RuntimeException ignoredAgain) { return fallback; }
        }
    }

    private static Map<String, Double> numericVariables(ScriptContext context) {
        Map<String, Double> variables = new HashMap<>(context.numbers());
        for (var entry : context.numbers().entrySet()) variables.put("var." + entry.getKey(), entry.getValue());
        variables.put("attack.damage", context.damage());
        return variables;
    }

    private static boolean boolAny(ScriptLineParser.Call call, boolean fallback, String... keys) {
        for (String key : keys) {
            String raw = call.params().get(key);
            if (raw == null) continue;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "true", "yes", "on", "1" -> true;
                case "false", "no", "off", "0" -> false;
                default -> throw new IllegalArgumentException("Invalid boolean for '" + key + "': " + raw);
            };
        }
        return fallback;
    }

    private static boolean compare(double first, double second, String comparator) {
        return switch (enumName(comparator)) {
            case "EQUALS", "EQUAL", "=" -> Math.abs(first - second) < 1.0E-7d;
            case "LOWER", "LOWER_OR_EQUAL", "<=" -> first <= second;
            case "GREATER", "GREATER_OR_EQUAL", ">=" -> first >= second;
            case "STRICTLY_LOWER", "<" -> first < second;
            case "STRICTLY_GREATER", ">" -> first > second;
            default -> throw new IllegalArgumentException("Unknown comparator: " + comparator);
        };
    }

    private static boolean timePeriod(String raw, long time) {
        long t = Math.floorMod(time, 24000L);
        return switch (enumName(raw)) {
            case "DAY" -> t >= 2000L && t <= 10000L;
            case "DUSK" -> t >= 14000L && t <= 18000L;
            case "NIGHT" -> t >= 14000L && t <= 22000L;
            case "DAWN" -> t >= 22000L || t <= 2000L;
            default -> throw new IllegalArgumentException("Unknown time period: " + raw);
        };
    }

    private static String variableName(ScriptLineParser.Call call) { return firstRequired(call.params(), "variable", "var", "v"); }
    private static String scope(ScriptLineParser.Call call) { return call.params().getOrDefault("scope", "SKILL"); }
    private static boolean between(double value, double a, double b) { return value >= Math.min(a, b) && value <= Math.max(a, b); }
    private static double dot(Vector3 a, Vector3 b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static Vector3 cross(Vector3 a, Vector3 b) { return new Vector3(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x()); }

    private static Vector3 orient(Vector3 value, Vector3 axis) {
        Vector3 z = axis.normalize();
        Vector3 up = Math.abs(z.y()) > .99 ? new Vector3(1, 0, 0) : new Vector3(0, 1, 0);
        Vector3 x = cross(up, z).normalize(), y = cross(z, x).normalize();
        return x.multiply(value.x()).add(y.multiply(value.y())).add(z.multiply(value.z()));
    }

    private static String require(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required parameter '" + key + "'");
        return value;
    }

    private static String firstRequired(Map<String, String> params, String... keys) {
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        throw new IllegalArgumentException("Missing required parameter (one of " + String.join(", ", keys) + ")");
    }

    private static String first(Map<String, String> params, String fallback, String... keys) {
        for (String key : keys) {
            String value = params.get(key);
            if (value != null) return value;
        }
        return fallback;
    }

    private static String registryPath(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        return colon >= 0 ? normalized.substring(colon + 1) : normalized;
    }

    private static ScriptLineParser.Call canonical(ScriptLineParser.Call call, String name) { return call.name().equals(name) ? call : new ScriptLineParser.Call(name, call.params()); }

    private static String canonicalCondition(String value) {
        return switch (norm(value)) {
            case "bool" -> "boolean";
            case "chance" -> "random_chance";
            case "variable_exists" -> "has_variable";
            case "ammo" -> "has_ammo";
            case "cantarget", "can_tgt", "ctgt" -> "can_target";
            default -> norm(value);
        };
    }

    private static String canonicalMechanic(String value) {
        return switch (norm(value)) {
            case "add_stat" -> "add_stat_modifier";
            case "remove_stat" -> "remove_stat_modifier";
            case "reduce_cd", "decrease_cooldown", "decrease_cd" -> "reduce_cooldown";
            case "apply_cd" -> "apply_cooldown";
            case "take_ammo" -> "consume_ammo";
            case "c", "dispatch_cmd", "cmd", "command", "execute_command", "execute_cmd", "run_command", "run_cmd" -> "dispatch_command";
            case "lightning_strike" -> "lightning";
            case "skill", "cast" -> "script";
            case "tp", "set_position", "set_pos", "setpos", "setposition", "set_location", "setlocation", "set_loc", "setloc", "move", "moveto", "move_to" -> "teleport";
            case "velocity", "setvel", "set_vel", "setvelocity" -> "set_velocity";
            case "deal_damage", "dmg", "deal_dmg", "dealdamage", "dealdmg", "attack", "atk" -> "damage";
            case "fire_arrow", "bowshoot", "bow_shoot", "shoot_bow" -> "shoot_arrow";
            case "helix" -> "draw_helix";
            case "line" -> "draw_line";
            case "parabola", "spawn_parabola" -> "draw_parabola";
            case "raytrace", "cast_ray", "ray_cast", "raycast" -> "ray_trace";
            case "sphere" -> "draw_sphere";
            case "add_vec" -> "add_vector";
            case "normalize" -> "normalize_vector";
            case "orient_vec" -> "orient_vector";
            case "copy_vector", "save_vec", "copy_vec" -> "save_vector";
            case "sub_vec", "sub_vector", "subvec" -> "subtract_vector";
            case "incr" -> "increment";
            case "set_bool" -> "set_boolean";
            case "set_float" -> "set_double";
            case "set_int" -> "set_integer";
            case "set_str" -> "set_string";
            case "set_vec" -> "set_vector";
            case "spawn_particle", "par" -> "particle";
            case "play_world_sound", "play_sound", "world_sound" -> "sound";
            case "play_player_sound" -> "player_sound";
            case "message", "msg", "send", "send_message", "send_msg" -> "tell";
            default -> norm(value);
        };
    }

    private static String enumName(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
    private static String norm(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
