package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import vn.svframe.svframelib.util.configobject.MapConfigObject;
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;
import vn.svframe.svframemmo.skill.ClassSkill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds fusion-only SVFrameMMO skill handlers from the Pokemon's four currently equipped moves.
 * Nothing is registered into the global SVFrameLib/SVFrameMMO skill registry.
 */
public final class CobblemonMoveSkillAdapter {
    private static final String CANONICAL_PREFIX = "COBBLEMON_MOVE_";
    private final FusionService fusionService;
    private final MoveSemanticRegistry semantics;

    public CobblemonMoveSkillAdapter(FusionService fusionService, MoveSemanticRegistry semantics) {
        this.fusionService = fusionService;
        this.semantics = semantics;
    }

    /** Snapshot exactly the active move set at fusion start and force it into temporary slots 1..4. */
    public Overlay snapshot(Pokemon pokemon) {
        LinkedHashMap<Integer, ClassSkill> skills = new LinkedHashMap<>();
        ArrayList<String> ids = new ArrayList<>(4);
        List<Move> currentMoves = pokemon.getMoveSet().getMoves();
        int slot = 1;
        for (Move move : currentMoves) {
            if (move == null || slot > 4) continue;
            String moveId = id(move.getName());
            MoveTemplate template = move.getTemplate();
            CobblemonMoveSkill handler = new CobblemonMoveSkill(defaultConfig(canonicalId(moveId), template), move.getName(), semantics, fusionService);
            skills.put(slot++, new ClassSkill(handler, 0, 1, true, true, false));
            ids.add(moveId);
        }
        if (skills.isEmpty()) throw new IllegalStateException("Pokemon has no active moves");
        return new Overlay(Map.copyOf(skills), List.copyOf(ids));
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
                "Fusion move: " + move.getName(),
                "Type: " + move.getElementalType().getName() + " / " + move.getDamageCategory().getName()));
        values.put("trigger", "CAST");
        values.put("categories", List.of("COBBLEMON_MOVE", move.getElementalType().getName().toUpperCase(Locale.ROOT)));
        values.put("parameters", parameters);
        return new MapConfigObject(key, values);
    }

    public static String canonicalId(String moveId) { return CANONICAL_PREFIX + id(moveId).toUpperCase(Locale.ROOT); }

    public static boolean isCanonicalSkillId(String skillId) {
        return skillId != null && skillId.trim().toUpperCase(Locale.ROOT).startsWith(CANONICAL_PREFIX);
    }

    public static String moveIdFromCanonical(String skillId) {
        if (!isCanonicalSkillId(skillId)) throw new IllegalArgumentException("Not a Cobblemon fusion move skill id: " + skillId);
        return id(skillId.trim().substring(CANONICAL_PREFIX.length()));
    }

    public static String id(String raw) {
        String compact = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (compact.isBlank()) throw new IllegalArgumentException("Move id must not be blank");
        return compact;
    }

    public record Overlay(Map<Integer, ClassSkill> skills, List<String> moveIds) { }
}
