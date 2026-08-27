package vn.svframe.svframemmo.experience.curve;

import vn.svframe.svframemmo.api.player.PlayerData;

public interface ExperienceCurve {
    ExperienceCurve DEFAULT = new ListExperienceCurve(100, 200, 300, 400, 500);

    long getExperience(PlayerData player, int level);
}
