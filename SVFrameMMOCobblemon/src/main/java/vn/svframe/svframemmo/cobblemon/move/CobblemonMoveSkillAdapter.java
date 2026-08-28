package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Pokemon;
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;
import vn.svframe.svframemmo.skill.ClassSkill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared immutable skill definitions compiled from Cobblemon's move registry. */
public final class CobblemonMoveSkillAdapter {
    private final FusionService fusionService;
    private final MoveSemanticRegistry semantics;
    private final ConcurrentHashMap<String, ClassSkill> definitions = new ConcurrentHashMap<>();

    public CobblemonMoveSkillAdapter(FusionService fusionService, MoveSemanticRegistry semantics) {
        this.fusionService = fusionService;
        this.semantics = semantics;
    }

    public void reload() {
        LinkedHashMap<String, ClassSkill> next = new LinkedHashMap<>();
        for (MoveTemplate move : Moves.all()) next.put(id(move.getName()), compile(move));
        definitions.clear();
        definitions.putAll(next);
    }

    public int size() { return definitions.size(); }

    public Overlay snapshot(Pokemon pokemon) {
        LinkedHashMap<Integer, ClassSkill> skills = new LinkedHashMap<>();
        java.util.ArrayList<String> ids = new java.util.ArrayList<>(4);
        List<Move> moves = pokemon.getMoveSet().getMoves();
        int slot = 1;
        for (Move move : moves) {
            if (move == null || slot > 4) continue;
            String moveId = id(move.getName());
            ClassSkill skill = definitions.computeIfAbsent(moveId, ignored -> compile(move.getTemplate()));
            skills.put(slot++, skill);
            ids.add(moveId);
        }
        if (skills.isEmpty()) throw new IllegalStateException("Pokemon has no active moves");
        return new Overlay(Map.copyOf(skills), List.copyOf(ids));
    }

    private ClassSkill compile(MoveTemplate move) {
        CobblemonMoveSkill handler = new CobblemonMoveSkill(move, semantics.resolve(move), fusionService);
        return new ClassSkill(handler, 0, 1, true, true, false);
    }

    public static String id(String raw) {
        String compact = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (compact.isBlank()) throw new IllegalArgumentException("Move id must not be blank");
        return compact;
    }

    public record Overlay(Map<Integer, ClassSkill> skills, List<String> moveIds) { }
}
