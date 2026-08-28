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
 * Full Cobblemon move catalog exposed as native SVFrameLib/SVFrameMMO skills.
 * Fusion reuses the same canonical handlers instead of maintaining a second skill implementation.
 */
public final class CobblemonMoveSkillAdapter {
    public static final String SOURCE_KEY = "cobblemon";
    private final FusionService fusionService;
    private final MoveSemanticRegistry semantics;
    private final ConcurrentHashMap<String, ClassSkill> definitions = new ConcurrentHashMap<>();
    private volatile boolean sourceRegistered;

    public CobblemonMoveSkillAdapter(FusionService fusionService, MoveSemanticRegistry semantics) {
        this.fusionService = fusionService;
        this.semantics = semantics;
    }

    /** Register source: cobblemon:<move> so normal SVFrameMMO class YAML can consume generated moves. */
    public synchronized void registerSkillSource() {
        if (sourceRegistered) return;
        SkillManager manager = SVFrameLib.inst().getSkills();
        manager.registerSkillHandlerSource(new SkillHandlerSource(SOURCE_KEY, this::sourceHandler));
        sourceRegistered = true;
    }

    /** Compile every live Cobblemon move and register one canonical handler in the global skill registry. */
    public synchronized void reload() {
        SkillManager manager = SVFrameLib.inst().getSkills();
        LinkedHashMap<String, ClassSkill> next = new LinkedHashMap<>();
        for (MoveTemplate move : Moves.all()) {
            String moveId = id(move.getName());
            String canonicalId = canonicalId(moveId);
            SkillHandler<?> existing = manager.getHandler(canonicalId);
            CobblemonMoveSkill handler;
            if (existing == null) {
                handler = new CobblemonMoveSkill(defaultConfig(canonicalId, move), move.getName(), semantics, fusionService);
                manager.registerSkillHandler(handler);
            } else if (existing instanceof CobblemonMoveSkill cobblemon) {
                handler = cobblemon;
            } else {
                throw new IllegalStateException("Cobblemon move skill ID collision: " + canonicalId + " -> " + existing.getClass().getName());
            }
            next.put(moveId, new ClassSkill(handler, 0, 1, true, true, false));
        }
        definitions.clear();
        definitions.putAll(next);
    }

    public int size() { return definitions.size(); }
    public Map<String, ClassSkill> definitions() { return Map.copyOf(definitions); }

    /** Fusion may only bind canonical handlers that are already present in SVFrameMMO/SVFrameLib's global skill registry. */
    public Overlay snapshot(Pokemon pokemon) {
        LinkedHashMap<Integer, ClassSkill> skills = new LinkedHashMap<>();
        ArrayList<String> ids = new ArrayList<>(4);
        List<Move> currentMoves = pokemon.getMoveSet().getMoves();
        int slot = 1;
        for (Move move : currentMoves) {
            if (move == null || slot > 4) continue;
            String moveId = id(move.getName());
            ClassSkill skill = definitions.get(moveId);
            if (skill == null) {
                SkillHandler<?> registered = SVFrameLib.inst().getSkills().getHandler(canonicalId(moveId));
                if (!(registered instanceof CobblemonMoveSkill handler))
                    throw new IllegalStateException("Cobblemon move is not registered as an SVFrameMMO skill: " + move.getName());
                skill = new ClassSkill(handler, 0, 1, true, true, false);
                definitions.putIfAbsent(moveId, skill);
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
            // Early class parsing can happen before Cobblemon populates Moves.all(). The handler resolves its live
            // MoveTemplate dynamically and SVFrameMMO is reparsed once COBBLEMON_INITIALISED fires.
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

    public static String canonicalId(String moveId) { return "COBBLEMON_MOVE_" + id(moveId).toUpperCase(Locale.ROOT); }

    public static String id(String raw) {
        String compact = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (compact.isBlank()) throw new IllegalArgumentException("Move id must not be blank");
        return compact;
    }

    public record Overlay(Map<Integer, ClassSkill> skills, List<String> moveIds) { }
}
