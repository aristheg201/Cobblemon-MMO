package vn.svframe.svframemmo.cobblemon.integration;

import vn.svframe.svframemmo.api.integration.SkillSourceBootstrap;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkillAdapter;

/** Loaded by SVFrameMMO before class definitions so source: cobblemon:<move> is restart-safe. */
public final class CobblemonSkillSourceEntrypoint implements SkillSourceBootstrap {
    @Override public void registerSkillSources() {
        // Force Integration static initialization first so its FusionService owns the canonical move handlers.
        SVFrameMMOCobblemon.fusions();
        CobblemonMoveSkillAdapter.registerSkillSource();
    }
}
