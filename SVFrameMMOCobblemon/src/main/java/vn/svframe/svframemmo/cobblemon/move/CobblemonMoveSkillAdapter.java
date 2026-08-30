package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Pokemon;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.manager.SkillManager;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.handler.SkillHandlerSource;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import vn.svframe.svframelib.util.configobject.MapConfigObject;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;
import vn.svframe.svframemmo.skill.ClassSkill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cobblemon is the provider/source-of-truth for Pokemon move skills.
 * Every live entry in {@link Moves} is projected into SVFrameLib/SVFrameMMO; no static move catalog is maintained here.
 */
public final class CobblemonMoveSkillAdapter {
    public static final String SOURCE_KEY = "cobblemon";
    public static final String REGISTRY_OWNER = "svframemmo_cobblemon";
    private static final String CANONICAL_PREFIX = "COBBLEMON_MOVE_";
    private static final ConcurrentHashMap<String, ClassSkill> DEFINITIONS = new ConcurrentHashMap<>();
    private static volatile CobblemonMoveSkillAdapter active;
    private static volatile boolean sourceRegistered;

    private final FusionService fusionService;
    private final MoveSemanticRegistry semantics;

    public CobblemonMoveSkillAdapter(FusionService fusionService, MoveSemanticRegistry semantics) {
        this.fusionService = fusionService;
        this.semantics = semantics;
        active = this;
    }

    /** Register source syntax {@code cobblemon:<move>} before SVFrameMMO definitions are parsed. */
    public static synchronized void registerSkillSource() {
        if (sourceRegistered) return;
        CobblemonMoveSkillAdapter adapter = requireActive();
        SkillManager manager = SVFrameLib.inst().getSkills();
        manager.registerSkillHandlerSource(new SkillHandlerSource(SOURCE_KEY, adapter::sourceHandler));
        sourceRegistered = true;
    }

    /** Re-project the complete live Cobblemon move registry into SVFrameMMO. */
    public static synchronized void reload() {
        CobblemonMoveSkillAdapter adapter = requireActive();
        SkillManager manager = SVFrameLib.inst().getSkills();
        LinkedHashMap<String, ClassSkill> next = new LinkedHashMap<>();
        int maxLevel = SVFrameMMOCobblemon.config().pokemonSkills.maxLevel;
        for (MoveTemplate move : Moves.all()) {
            String moveId = id(move.getName());
            String canonicalId = canonicalId(moveId);
            SkillHandler<?> existing = manager.getHandler(canonicalId);
            CobblemonMoveSkill handler;
            if (existing == null) {
                handler = new CobblemonMoveSkill(defaultConfig(canonicalId, move), move.getName(), adapter.semantics, adapter.fusionService);
                manager.registerSkillHandler(handler);
            } else if (existing instanceof CobblemonMoveSkill) {
                handler = new CobblemonMoveSkill(defaultConfig(canonicalId, move), move.getName(), adapter.semantics, adapter.fusionService);
            } else {
                throw new IllegalStateException("Cobblemon move skill ID collision: " + canonicalId + " -> " + existing.getClass().getName());
            }

            ClassSkill definition = new ClassSkill(handler, 0, maxLevel, false, true, maxLevel > 1);
            if (next.putIfAbsent(moveId, definition) != null)
                throw new IllegalStateException("Two Cobblemon moves normalize to the same SVFrameMMO skill ID: " + moveId);
        }

        if (next.size() != Moves.count())
            throw new IllegalStateException("Cobblemon provider projection mismatch: registry=" + Moves.count() + ", projected=" + next.size());

        DEFINITIONS.clear();
        DEFINITIONS.putAll(next);
        SVFrameMMO.externalSkills().replace(REGISTRY_OWNER, next.values());

        if (SVFrameMMO.externalSkills().getByOwner(REGISTRY_OWNER).size() != next.size())
            throw new IllegalStateException("SVFrameMMO external skill registry did not retain the complete Cobblemon move catalog");
    }

    public static int size() { return DEFINITIONS.size(); }
    public static int providerSize() { return Moves.count(); }
    public static Map<String, ClassSkill> definitions() { return Map.copyOf(DEFINITIONS); }

    /** Snapshot exactly the Pokemon's current move slots at fusion start; normal global skill ownership is untouched. */
    public Overlay snapshot(Pokemon pokemon) {
        LinkedHashMap<Integer, ClassSkill> skills = new LinkedHashMap<>();
        ArrayList<String> ids = new ArrayList<>(4);
        List<Move> currentMoves = pokemon.getMoveSet().getMoves();
        int slot = 1;
        for (Move move : currentMoves) {
            if (move == null || slot > 4) continue;
            String moveId = id(move.getName());
            ClassSkill skill = DEFINITIONS.get(moveId);
            if (skill == null) {
                MoveTemplate template = move.getTemplate();
                CobblemonMoveSkill handler = new CobblemonMoveSkill(defaultConfig(canonicalId(moveId), template),
                        template.getName(), semantics, fusionService);
                int maxLevel = SVFrameMMOCobblemon.config().pokemonSkills.maxLevel;
                skill = new ClassSkill(handler, 0, maxLevel, false, true, maxLevel > 1);
                DEFINITIONS.putIfAbsent(moveId, skill);
            }
            skills.put(slot++, skill);
            ids.add(moveId);
        }
        if (skills.isEmpty()) throw new IllegalStateException("Pokemon has no active moves");
        return new Overlay(Map.copyOf(skills), List.copyOf(ids));
    }

    private SkillHandler<?> sourceHandler(ConfigObject config, String internal) {
        String requested = id(internal);
        MoveTemplate move = Moves.getByName(requested);
        String key = config != null && config.hasKey() ? config.getKey() : canonicalId(requested);
        return new CobblemonMoveSkill(mergeConfig(key, requested, move, config), requested, semantics, fusionService);
    }

    static MapConfigObject defaultConfig(String key, MoveTemplate move) {
        CobblemonMoveProfile profile = CobblemonMoveProfile.of(move);
        var progression = SVFrameMMOCobblemon.config().pokemonSkills;
        Map<String, Object> parameters = new LinkedHashMap<>();
        double cooldown = Math.max(0d, profile.cooldownSeconds());
        parameters.put("cooldown", linear(cooldown, -cooldown * progression.cooldownReductionPerLevel,
                cooldown * progression.minimumCooldownMultiplier));
        parameters.put("damage", linear(Math.max(0d, profile.baseDamage()), Math.max(0d, profile.baseDamage()) * progression.damagePerLevel));
        parameters.put("healing", linear(Math.max(0d, profile.healBase()), Math.max(0d, profile.healBase()) * progression.healingPerLevel));
        parameters.put("mana", 0d);
        parameters.put("stamina", 0d);
        parameters.put("timer", 0d);
        parameters.put("delay", 0d);

        ArrayList<String> lore = new ArrayList<>();
        String description = move.getDescription().getString();
        if (!description.isBlank()) lore.add(description);
        lore.add("&7Type: &f" + move.getElementalType().getName() + " &8/ &f" + move.getDamageCategory().getName());
        if (profile.baseDamage() > 0d) lore.add("&cDamage: &f{damage}");
        if (profile.healBase() > 0d) lore.add("&aHealing: &f{healing}");
        lore.add("&eCooldown: &6{cooldown}s");
        lore.add("&7Provider: Cobblemon");

        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("source", SOURCE_KEY + ":" + id(move.getName()));
        values.put("name", move.getDisplayName().getString());
        values.put("icon", PokemonSkillIconResolver.iconConfig(move));
        values.put("lore", lore);
        values.put("trigger", "CAST");
        values.put("categories", categories(move, profile));
        values.put("parameters", parameters);
        return new MapConfigObject(key, values);
    }

    static List<String> categories(MoveTemplate move, CobblemonMoveProfile profile) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        String type = token(move.getElementalType().getName());
        String category = token(move.getDamageCategory().getName());
        String target = token(move.getTarget().name());
        categories.add("COBBLEMON_MOVE");
        categories.add(type);
        categories.add("COBBLEMON_TYPE_" + type);
        categories.add("COBBLEMON_CATEGORY_" + category);
        categories.add("COBBLEMON_TARGET_" + target);
        categories.add("COBBLEMON_EXECUTOR_" + profile.executor().name());
        categories.add(move.getPower() > 0d ? "COBBLEMON_DAMAGE" : "COBBLEMON_STATUS");
        if (move.getPriority() > 0) categories.add("COBBLEMON_PRIORITY");
        if (profile.requiresSingleTarget()) categories.add("COBBLEMON_SINGLE_TARGET");
        if (profile.executor() == CobblemonMoveProfile.Executor.AOE) categories.add("COBBLEMON_AOE");
        return List.copyOf(categories);
    }

    @SuppressWarnings("unchecked")
    private static MapConfigObject mergeConfig(String key, String requested, MoveTemplate move, ConfigObject configured) {
        LinkedHashMap<String, Object> merged;
        if (move == null) {
            merged = new LinkedHashMap<>();
            merged.put("source", SOURCE_KEY + ":" + requested);
            merged.put("name", requested);
            merged.put("icon", Map.of("item", "cobblemon:normal_gem"));
            merged.put("lore", List.of("Cobblemon move: " + requested, "&eCooldown: &6{cooldown}s", "Provider: cobblemon"));
            merged.put("trigger", "CAST");
            merged.put("categories", List.of("COBBLEMON_MOVE"));
            merged.put("parameters", new LinkedHashMap<>(Map.of(
                    "cooldown", 1.5d, "damage", 1d, "healing", 0d,
                    "mana", 0d, "stamina", 0d, "timer", 0d, "delay", 0d)));
        } else merged = new LinkedHashMap<>(defaultConfig(key, move).asMap());
        if (configured instanceof MapConfigObject map) {
            for (Map.Entry<String, Object> entry : map.asMap().entrySet()) {
                if ("source".equalsIgnoreCase(entry.getKey())) continue;
                if ("parameters".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Map<?, ?> raw) {
                    LinkedHashMap<String, Object> params = new LinkedHashMap<>((Map<String, Object>) merged.get("parameters"));
                    raw.forEach((k, v) -> params.put(String.valueOf(k), v));
                    merged.put("parameters", params);
                } else merged.put(entry.getKey(), entry.getValue());
            }
        }
        return new MapConfigObject(key, merged);
    }

    private static Map<String, Object> linear(double base, double perLevel) {
        LinkedHashMap<String, Object> formula = new LinkedHashMap<>();
        formula.put("base", base);
        formula.put("per-level", perLevel);
        return formula;
    }

    private static Map<String, Object> linear(double base, double perLevel, double min) {
        LinkedHashMap<String, Object> formula = new LinkedHashMap<>();
        formula.put("base", base);
        formula.put("per-level", perLevel);
        formula.put("min", Math.max(0d, min));
        return formula;
    }

    private static CobblemonMoveSkillAdapter requireActive() {
        CobblemonMoveSkillAdapter adapter = active;
        if (adapter == null) throw new IllegalStateException("Cobblemon move adapter has not been initialized");
        return adapter;
    }

    public static String canonicalId(String moveId) { return CANONICAL_PREFIX + id(moveId).toUpperCase(Locale.ROOT); }
    public static boolean isCanonicalSkillId(String skillId) {
        return skillId != null && skillId.trim().toUpperCase(Locale.ROOT).startsWith(CANONICAL_PREFIX);
    }
    public static String moveIdFromCanonical(String skillId) {
        if (!isCanonicalSkillId(skillId)) throw new IllegalArgumentException("Not a Cobblemon move skill id: " + skillId);
        return id(skillId.trim().substring(CANONICAL_PREFIX.length()));
    }
    public static String id(String raw) {
        String compact = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (compact.isBlank()) throw new IllegalArgumentException("Move id must not be blank");
        return compact;
    }
    private static String token(String raw) {
        String value = raw == null ? "UNKNOWN" : raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return value.isBlank() ? "UNKNOWN" : value;
    }

    public record Overlay(Map<Integer, ClassSkill> skills, List<String> moveIds) { }
}
