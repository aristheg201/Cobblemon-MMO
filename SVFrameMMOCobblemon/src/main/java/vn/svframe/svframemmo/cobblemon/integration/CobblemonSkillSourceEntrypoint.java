package vn.svframe.svframemmo.cobblemon.integration;

import vn.svframe.svframemmo.api.integration.SkillSourceBootstrap;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

/** Loaded by SVFrameMMO before class definitions so source: cobblemon:<move> is restart-safe. */
public final class CobblemonSkillSourceEntrypoint implements SkillSourceBootstrap {
    @Override public void registerSkillSources() { SVFrameMMOCobblemon.fusions().registerMoveSkillSource(); }
}
