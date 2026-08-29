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
    private volatile Map<String, List<ExperienceSourceDefinition>> classSources = Map.of();
    private volatile Map<String, List<ExperienceSourceDefinition>> professionSources = Map.of();

    public synchronized void reload(ClassManager classes, ProfessionManager professions, Map<String, List<String>> shared) {
        LinkedHashMap<String, List<ExperienceSourceDefinition>> nextClasses = new LinkedHashMap<>();
        for (PlayerClass playerClass : classes.getAll())
            nextClasses.put(playerClass.getId(), compile(playerClass.getMainExperienceSources(), shared));

        LinkedHashMap<String, List<ExperienceSourceDefinition>> nextProfessions = new LinkedHashMap<>();
        for (Profession profession : professions.getAll())
            nextProfessions.put(profession.getId(), compile(profession.getExperienceSources(), shared));

        classSources = immutable(nextClasses);
        professionSources = immutable(nextProfessions);
    }

    public void accept(PlayerData data, ExperienceSignal signal) { accept(data, signal, null); }

    public void accept(PlayerData data, ExperienceSignal signal, ExperienceHologramRuntime.HologramLocation hologramLocation) {
        acceptClass(data, signal, hologramLocation);
        if (data == null || signal == null || !data.isOnline() || signal.units() <= 0d) return;
        for (Profession profession : SVFrameMMO.professions().getAll()) acceptProfession(data, profession, signal, hologramLocation);
    }

    /** Dispenses a gameplay signal only to the player's active class. */
    public void acceptClass(PlayerData data, ExperienceSignal signal) { acceptClass(data, signal, null); }

    public void acceptClass(PlayerData data, ExperienceSignal signal, ExperienceHologramRuntime.HologramLocation hologramLocation) {
        if (data == null || signal == null || !data.isOnline() || signal.units() <= 0d) return;
        List<ExperienceSourceDefinition> main = classSources.getOrDefault(data.getProfess().getId(), List.of());
        for (ExperienceSourceDefinition source : main) {
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
        if (data == null || profession == null || signal == null || !data.isOnline() || signal.units() <= 0d) return;
        List<ExperienceSourceDefinition> sources = professionSources.getOrDefault(profession.getId(), List.of());
        for (ExperienceSourceDefinition source : sources) {
            if (!source.matches(signal)) continue;
            double value = source.experience(signal);
            if (value <= 0d) continue;
            if (hologramLocation == null) data.getProfessions().giveExperience(profession, value, EXPSource.SOURCE);
            else ExperienceHologramRuntime.instance().giveProfession(data, profession, value, EXPSource.SOURCE, hologramLocation);
        }
    }

    public int classSourceCount() { return classSources.values().stream().mapToInt(List::size).sum(); }
    public int professionSourceCount() { return professionSources.values().stream().mapToInt(List::size).sum(); }

    private static List<ExperienceSourceDefinition> compile(List<String> lines, Map<String, List<String>> shared) {
        ArrayList<ExperienceSourceDefinition> result = new ArrayList<>();
        for (String line : lines) expand(ExperienceSourceDefinition.parse(line), shared, result, new LinkedHashSet<>());
        return List.copyOf(result);
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

    private static Map<String, List<ExperienceSourceDefinition>> immutable(Map<String, List<ExperienceSourceDefinition>> input) {
        LinkedHashMap<String, List<ExperienceSourceDefinition>> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }
}
