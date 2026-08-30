package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svframemmo.cobblemon.integration.CobblemonMoveVfxService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Machine-generated, provider-backed move configuration snapshots.
 *
 * <p>The directory is regenerated from the live Cobblemon move registry. New moves supplied by a newer Cobblemon
 * build or another mod therefore appear automatically without shipping a hand-maintained SVFrameMMO move list.</p>
 */
public final class CobblemonMoveConfigGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir()
            .resolve("SVFrameMMOCobblemon/generated-moves");
    private static final String MANIFEST = "_manifest.json";

    private CobblemonMoveConfigGenerator() { }

    public static synchronized int regenerate(CobblemonMoveVfxService vfx) throws IOException {
        Files.createDirectories(ROOT);
        ArrayList<MoveTemplate> moves = new ArrayList<>(Moves.all());
        moves.sort(Comparator.comparing(move -> CobblemonMoveSkillAdapter.id(move.getName())));
        Set<String> expected = new HashSet<>();
        MoveSemanticRegistry semantics = new MoveSemanticRegistry();

        for (MoveTemplate move : moves) {
            String id = CobblemonMoveSkillAdapter.id(move.getName());
            String fileName = id + ".json";
            expected.add(fileName);
            writeIfChanged(ROOT.resolve(fileName), GSON.toJson(snapshot(move, semantics.resolve(move), vfx)) + "\n");
        }

        try (var files = Files.list(ROOT)) {
            for (Path path : files.toList()) {
                String name = path.getFileName().toString();
                if (Files.isRegularFile(path) && name.endsWith(".json") && !MANIFEST.equals(name) && !expected.contains(name))
                    Files.deleteIfExists(path);
            }
        }

        LinkedHashMap<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("provider", "cobblemon");
        manifest.put("provider-size", Moves.count());
        manifest.put("generated-configs", moves.size());
        manifest.put("specific-action-effects", vfx.planCount());
        manifest.put("generic-action-effect-profiles", vfx.genericPlanCount());
        manifest.put("source-format", "cobblemon:<move>");
        manifest.put("note", "Machine generated from the live Cobblemon registry; files are replaced when the provider reloads.");
        writeIfChanged(ROOT.resolve(MANIFEST), GSON.toJson(manifest) + "\n");
        return moves.size();
    }

    private static Map<String, Object> snapshot(MoveTemplate move, MoveSemantic semantic, CobblemonMoveVfxService vfx) {
        String id = CobblemonMoveSkillAdapter.id(move.getName());
        CobblemonMoveProfile profile = CobblemonMoveProfile.of(move);
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("provider", "cobblemon");
        root.put("source", "cobblemon:" + id);
        root.put("skill-id", CobblemonMoveSkillAdapter.canonicalId(id));
        root.put("name", move.getDisplayName().getString());
        root.put("description", move.getDescription().getString());

        LinkedHashMap<String, Object> cobblemon = new LinkedHashMap<>();
        cobblemon.put("move-id", move.getName());
        cobblemon.put("move-number", move.getNum());
        cobblemon.put("type", move.getElementalType().getName());
        cobblemon.put("damage-category", move.getDamageCategory().getName());
        cobblemon.put("target", move.getTarget().name());
        cobblemon.put("power", move.getPower());
        cobblemon.put("accuracy", move.getAccuracy());
        cobblemon.put("pp", move.getPp());
        cobblemon.put("max-pp", move.getMaxPp());
        cobblemon.put("priority", move.getPriority());
        cobblemon.put("crit-ratio", move.getCritRatio());
        Double[] chances = move.getEffectChances();
        cobblemon.put("effect-chances", chances == null ? List.of() : Arrays.asList(chances));
        root.put("cobblemon", cobblemon);

        LinkedHashMap<String, Object> classification = new LinkedHashMap<>();
        classification.put("executor", profile.executor().name());
        classification.put("type", profile.type());
        classification.put("damage-category", profile.damageCategory());
        classification.put("target-type", profile.targetType());
        classification.put("single-target", profile.requiresSingleTarget());
        classification.put("categories", CobblemonMoveSkillAdapter.categories(move, profile));
        root.put("classification", classification);

        LinkedHashMap<String, Object> gameplay = new LinkedHashMap<>();
        gameplay.put("base-damage", profile.baseDamage());
        gameplay.put("range", profile.range());
        gameplay.put("radius", profile.radius());
        gameplay.put("cooldown-seconds", profile.cooldownSeconds());
        gameplay.put("dash-strength", profile.dashStrength());
        gameplay.put("semantics", semanticMap(semantic));
        root.put("gameplay", gameplay);

        LinkedHashMap<String, Object> presentation = new LinkedHashMap<>();
        presentation.put("source", vfx.presentationSource(move));
        presentation.put("specific-action-effect", vfx.hasSpecificPlan(id));
        presentation.put("generic-fallback-available", vfx.hasGenericPlan());
        presentation.put("animation", "Cobblemon timeline timing; player swing is used because the caster remains a PlayerEntity");
        presentation.put("particles", "Cobblemon Snowstorm/action_effect particles");
        presentation.put("sounds", "Cobblemon action_effect sounds");
        root.put("presentation", presentation);

        root.put("svframemmo", new LinkedHashMap<>(CobblemonMoveSkillAdapter.defaultConfig(
                CobblemonMoveSkillAdapter.canonicalId(id), move).asMap()));
        return root;
    }

    private static Map<String, Object> semanticMap(MoveSemantic semantic) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        ArrayList<Map<String, Object>> stages = new ArrayList<>();
        for (MoveSemantic.StageChange change : semantic.stages()) {
            LinkedHashMap<String, Object> stage = new LinkedHashMap<>();
            stage.put("target", change.target().name());
            stage.put("stat", change.stat().name());
            stage.put("stages", change.stages());
            stages.add(stage);
        }
        result.put("stages", stages);
        result.put("status", semantic.status().name());
        result.put("status-chance", semantic.statusChance());
        result.put("heal-fraction", semantic.healFraction());
        result.put("drain-fraction", semantic.drainFraction());
        result.put("recoil-fraction", semantic.recoilFraction());
        result.put("multi-hit-min", semantic.multiHitMin());
        result.put("multi-hit-max", semantic.multiHitMax());
        result.put("protect", semantic.protect());
        return result;
    }

    private static void writeIfChanged(Path target, String content) throws IOException {
        if (Files.exists(target) && Files.readString(target).equals(content)) return;
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content);
        try { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
