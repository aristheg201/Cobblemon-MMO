package vn.svframe.svframemmo.cobblemon;

import net.fabricmc.api.ModInitializer;
import vn.svframe.svframemmo.cobblemon.fusion.render.FusionMorphNetworking;

/** Common bootstrap shared by dedicated server and client; contains no SVFrameMMO/SVFrameLib runtime dependency. */
public final class SVFrameMMOCobblemonCommon implements ModInitializer {
    @Override
    public void onInitialize() {
        FusionMorphNetworking.register();
    }
}
