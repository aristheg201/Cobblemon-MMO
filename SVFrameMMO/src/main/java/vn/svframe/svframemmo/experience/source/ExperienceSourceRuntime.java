package vn.svframe.svframemmo.experience.source;

import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.PlayerClass;
import vn.svframe.svframemmo.experience.EXPSource;
import vn.svframe.svframemmo.experience.Profession;
import vn.svframe.svframemmo.manager.ClassManager;
import vn.svframe.svframemmo.manager.ProfessionManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiled runtime index for class and profession gameplay EXP sources. */
public final class ExperienceSourceRuntime {
    /** class id -> signal type -> matching definitions */
    private volatile Map<String, Map<String, List<ExperienceSourceDefinition>>> classSources = Map.of();
    /** profession id -> signal type -> matching definitions */
    private volatile Map<String, Map<String, List<ExperienceSourceDefinition>>> professionSources = Map.of();
    /** signal type -> only professions which actually listen for that signal */
    private volatile Map<String, List<ProfessionBucket>> professionsBySignal = Map.of();

    public synchronized void reload(ClassManager classes, ProfessionManager professions, Map<String, List<String>> shared) {
        LinkedHashMap<String, Map<String, List<ExperienceSourceDefinition>>> nextClasses = new LinkedHashMap<>();
        for (PlayerClass playerClass : classes.getAll())
            nextClasses.put(playerClass.getId(), compileIndexed(playerClass.getMainExperienceSources(), shared));

        LinkedHashMap<String, Map<String, List<ExperienceSourceDefinition>>> nextProfessions = new LinkedHashMap<>();
        LinkedHashMap<String, List<ProfessionBucket>> nextBySignal = new LinkedHashMap<>();
        for (Profession profession : professions.getAll()) {
            Map<String, List<ExperienceSourceDefinition>> indexed = compileIndexed(profession.getExperienceSources(), shared);
            nextProfessions.put(profession.getId(), indexed);
            indexed.forEach((type, definitions) -> nextBySignal.computeIfAbsent(type, ignored -> new ArrayList<>())
                    .add(new ProfessionBucket(profession, definitions)));
        }

        classSources = immutableNested(nextClasses);
        professionSources = immutableNested(nextProfessions);
        professionsBySignal = immutableBuckets(nextBySignal);
    }

    public void accept(PlayerData data, ExperienceSignal signal) { accept(data, signal, null); }

    public void accept(PlayerData data, ExperienceSignal signal, ExperienceHologramRuntime.HologramLocation hologramLocation) {
        acceptClass(data, signal, hologramLocation);
        if (!valid(data, signal)) return;
        for (ProfessionBucket bucket : professionsBySignal.getOrDefault(signal.type(), List.of()))
            applyProfession(data, bucket.profession(), bucket.definitions(), signal, hologramLocation);
    }

    /** Dispenses a gameplay signal only to the player's active class. */
    public void acceptClass(PlayerData data, ExperienceSignal signal) { acceptClass(data, signal, null); }

    public void acceptClass(PlayerData data, ExperienceSignal signal, ExperienceHologramRuntime.HologramLocation hologramLocation) {
        if (!valid(data, signal)) return;
        Map<String, List<ExperienceSourceDefinition>> byType = classSources.get(data.getProfess().getId());
        if (byType == null) return;
        for (ExperienceSourceDefinition source : byType.getOrDefault(signal.type(), List.of())) {
            if (!source.matches(signal)) continue;
            double value = source.experience(signal);
            if (value <= 0d) continue;
            if (hologramLocation == null) data.giveExperience(value, EXPSource.SOURCE);
            else ExperienceHologramRuntime.instance().giveClass(data, value, EXPSource.SOURCE, hologramLocation);
        }
    }

    /** Dispenses a gameplay signal to one concrete profession (used by profession-specific formulas). */
    public void acceptProfession(PlayerData data, Profession profession, ExperienceSignal signal) { acceptProfession(data, profession, signal, null); }

    public void acceptProfession(PlayerData data, Profession profession, ExperienceSignal signal,
                                 ExperienceHologramRuntime.HologramLocation hologramLocation) {
        if (!valid(data, signal) || profession == null) return;
        Map<String, List<ExperienceSourceDefinition>> byType = professionSources.get(profession.getId());
        if (byType == null) return;
        applyProfession(data, profession, byType.getOrDefault(signal.type(), List.of()), signal, hologramLocation);
    }

    public int classSourceCount() { return count(classSources); }
    public int professionSourceCount() { return count(professionSources); }

    private static void applyProfession(PlayerData data, Profession profession, List<ExperienceSourceDefinition> definitions,
                                        ExperienceSignal signal, ExperienceHologramRuntime.HologramLocation hologramLocation) {
        for (ExperienceSourceDefinition source : definitions) {
            if (!source.matches(signal)) continue;
            double value = source.experience(signal);
            if (value <= 0d) continue;
            if (hologramLocation == null) data.getProfessions().giveExperience(profession, value, EXPSource.SOURCE);
            else ExperienceHologramRuntime.instance().giveProfession(data, profession, value, EXPSource.SOURCE, hologramLocation);
        }
    }

    private static boolean valid(PlayerData data, ExperienceSignal signal) {
        return data != null && signal != null && data.isOnline() && signal.units() > 0d;
    }

    private static Map<String, List<ExperienceSourceDefinition>> compileIndexed(List<String> lines, Map<String, List<String>> shared) {
        ArrayList<ExperienceSourceDefinition> expanded = new ArrayList<>();
        for (String line : lines) expand(ExperienceSourceDefinition.parse(line), shared, expanded, new LinkedHashSet<>());

        LinkedHashMap<String, List<ExperienceSourceDefinition>> indexed = new LinkedHashMap<>();
        for (ExperienceSourceDefinition source : expanded)
            indexed.computeIfAbsent(source.type(), ignored -> new ArrayList<>()).add(source);

        LinkedHashMap<String, List<ExperienceSourceDefinition>> immutable = new LinkedHashMap<>();
        indexed.forEach((type, definitions) -> immutable.put(type, List.copyOf(definitions)));
        return Map.copyOf(immutable);
    }

    private static void expand(ExperienceSourceDefinition source, Map<String, List<String>> shared,
                               List<ExperienceSourceDefinition> output, Set<String> stack) {
        if (!source.isFrom()) {
            output.add(source);
            return;
        }
        String id = ExperienceSourceGroups.normalize(source.referencedSource());
        if (id.isBlank()) throw new IllegalArgumentException("from{} experience source requires source=<id>");
        if (!stack.add(id)) throw new IllegalArgumentException("Recursive experience source group: " + stack + " -> " + id);
        List<String> nested = shared.get(id);
        if (nested == null || nested.isEmpty()) throw new IllegalArgumentException("Unknown experience source group: " + id);
        for (String line : nested) expand(ExperienceSourceDefinition.parse(line), shared, output, stack);
        stack.remove(id);
    }

    private static Map<String, Map<String, List<ExperienceSourceDefinition>>> immutableNested(
            Map<String, Map<String, List<ExperienceSourceDefinition>>> input) {
        LinkedHashMap<String, Map<String, List<ExperienceSourceDefinition>>> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> copy.put(key, Map.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static Map<String, List<ProfessionBucket>> immutableBuckets(Map<String, List<ProfessionBucket>> input) {
        LinkedHashMap<String, List<ProfessionBucket>> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static int count(Map<String, Map<String, List<ExperienceSourceDefinition>>> indexed) {
        int total = 0;
        for (Map<String, List<ExperienceSourceDefinition>> byType : indexed.values())
            for (List<ExperienceSourceDefinition> definitions : byType.values()) total += definitions.size();
        return total;
    }

    private record ProfessionBucket(Profession profession, List<ExperienceSourceDefinition> definitions) { }
}
