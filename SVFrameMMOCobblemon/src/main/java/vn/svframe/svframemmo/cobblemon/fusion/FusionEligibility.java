package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Potara tier eligibility derived from Cobblemon species labels, never a hard-coded species list. */
public final class FusionEligibility {
    public boolean allows(FusionTier tier, Pokemon pokemon) {
        if (tier == FusionTier.DANCE || tier == FusionTier.GOD) return true;
        Set<String> labels = normalizedLabels(pokemon);
        boolean starter = labels.contains("starter");
        boolean ultraBeast = labels.contains("ultra_beast");
        boolean paradox = labels.contains("paradox");
        boolean legendary = labels.contains("legendary");
        boolean mythical = labels.contains("mythical");
        boolean normal = !starter && !ultraBeast && !paradox && !legendary && !mythical;
        return switch (tier) {
            case BASIC -> starter;
            case LEVEL_2 -> normal;
            case ADVANCEMENT -> starter || normal || ultraBeast || paradox;
            case GOD, DANCE -> true;
        };
    }

    private static Set<String> normalizedLabels(Pokemon pokemon) {
        HashSet<String> result = new HashSet<>();
        for (String label : pokemon.getSpecies().getLabels()) {
            if (label != null) result.add(label.trim().toLowerCase(Locale.ROOT));
        }
        return result;
    }
}
