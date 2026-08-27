package vn.svframe.svframemmo.experience.curve;

import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.ArrayList;
import java.util.List;

public final class ListExperienceCurve implements ExperienceCurve {
    private final List<Long> experience;

    public ListExperienceCurve(long... values) {
        if (values == null || values.length == 0) throw new IllegalArgumentException("There must be at least one experience value");
        this.experience = new ArrayList<>(values.length);
        for (long value : values) this.experience.add(value);
    }

    public ListExperienceCurve(List<Long> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("There must be at least one experience value");
        this.experience = List.copyOf(values);
    }

    @Override
    public long getExperience(PlayerData player, int level) {
        if (level <= 0) throw new IllegalArgumentException("Level must be strictly positive");
        return experience.get(Math.min(level, experience.size()) - 1);
    }

    public List<Long> values() { return List.copyOf(experience); }
}
