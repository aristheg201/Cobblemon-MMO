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
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;
import vn.svframe.svframemmo.skill.ClassSkill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exposes the complete live Cobblemon move catalog as native SVFrameMMO skills.
 * Fusion separately snapshots exactly the selected Pokemon's four current moves and force-binds only those four.
 */
public final class CobblemonMoveSkillAdapter {
    public static final String SOURCE_KEY = "cobblemon";
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

    /** Register source: cobblemon:<move> before SVFrameMMO class definitions are parsed. */
    public static synchronized void registerSkillSource() {
        if (sourceRegistered) return;
        CobblemonMoveSkillAdapter adapter = requireActive();
        SkillManager manager = SVFrameLib.inst().getSkills();
        manager.registerSkillHandlerSource(new SkillHandlerSource(SOURCE_KEY, adapter::sourceHandler));
        sourceRegistered = true;
    }

    /** Register every live Cobblemon move as one canonical SVFrameMMO/SVFrameLib handler. */
    public static synchronized void reload() {
        CobblemonMoveSkillAdapter adapter = requireActive();
        SkillManager manager = SVFrameLib.inst().getSkills();
        LinkedHashMap<String, ClassSkill> next = new LinkedHashMap<>();
        for (MoveTemplate move : Moves.all()) {
            String moveId = id(move.getName());
            String canonicalId = canonicalId(moveId);
            SkillHandler<?> existing = manager.getHandler(canonicalId);
            CobblemonMoveSkill handler;
            if (existing == null) {
                handler = new CobblemonMoveSkill(defaultConfig(canonicalId, move), move.getName(), adapter.semantics, adapter.fusionService);
                manager.registerSkillHandler(handler);
            } else if (existing instanceof CobblemonMoveSkill cobblemon) {
                handler = cobblemon;
            } else {
                throw new IllegalStateException("Cobblemon move skill ID collision: " + canonicalId + " -> " + existing.getClass().getName());
            }
            next.put(moveId, new ClassSkill(handler, 0, 1, true, true, false));
        }
        DEFINITIONS.clear();
        DEFINITIONS.putAll(next);
    }

    public static int size() { return DEFINITIONS.size(); }
    public static Map<String, ClassSkill> definitions() { return Map.copyOf(DEFINITIONS); }

    /** Snapshot exactly the Pokemon's current move slots at fusion start; normal global skill availability is untouched. */
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
                SkillHandler<?> registered = SVFrameLib.inst().getSkills().getHandler(canonicalId(moveId));
                if (!(registered instanceof CobblemonMoveSkill handler))
                    throw new IllegalStateException("Cobblemon move is not registered as an SVFrameMMO skill: " + move.getName());
                skill = new ClassSkill(handler, 0, 1, true, true, false);
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
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("cooldown", profile.cooldownSeconds());
        parameters.put("mana", 0d);
        parameters.put("stamina", 0d);
        parameters.put("timer", 0d);
        parameters.put("delay", 0d);
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("name", move.getDisplayName().getString());
        values.put("lore", List.of(move.getDescription().getString(),
                "Cobblemon move: " + move.getName(),
                "Type: " + move.getElementalType().getName() + " / " + move.getDamageCategory().getName()));
        values.put("trigger", "CAST");
        values.put("categories", List.of("COBBLEMON_MOVE", move.getElementalType().getName().toUpperCase(Locale.ROOT)));
        values.put("parameters", parameters);
        return new MapConfigObject(key, values);
    }

    @SuppressWarnings("unchecked")
    private static MapConfigObject mergeConfig(String key, String requested, MoveTemplate move, ConfigObject configured) {
        LinkedHashMap<String, Object> merged;
        if (move == null) {
            merged = new LinkedHashMap<>();
            merged.put("name", requested);
            merged.put("lore", List.of("Cobblemon move: " + requested));
            merged.put("trigger", "CAST");
            merged.put("parameters", new LinkedHashMap<>(Map.of("cooldown", 1.5d, "mana", 0d, "stamina", 0d, "timer", 0d, "delay", 0d)));
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

    public record Overlay(Map<Integer, ClassSkill> skills, List<String> moveIds) { }
}
