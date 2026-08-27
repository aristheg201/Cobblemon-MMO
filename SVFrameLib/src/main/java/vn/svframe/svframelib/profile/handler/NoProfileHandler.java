package vn.svframe.svframelib.profile.handler;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.module.MMOPlugin;
import vn.svframe.svframelib.profile.SessionUpdateReason;
import net.minecraft.util.Identifier;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;

import java.util.ArrayList;
import java.util.List;

/** Native Fabric equivalent of SVFrameLib 1.7.1's no-profile handler. */
public final class NoProfileHandler implements ProfileHandler {
    private final List<Identifier> modules;

    public NoProfileHandler() {
        this.modules = SVFrameLib.inst().getMMOPlugins().stream()
                .filter(MMOPlugin::hasData)
                .map(plugin -> Identifier.of(plugin.getNamespacedKey(), "plugin"))
                .toList();
    }

    @Override
    public void onStartup() {
        SVFrameLibFabricMod.schedule(20, () -> MMOPlayerData.getLoaded().forEach(data -> {
            if (!data.hasProfileSession()) data.chooseProfile(null, SessionUpdateReason.LOGIN);
        }));
    }

    public void onLogin(MMOPlayerData data) {
        if (!data.hasProfileSession()) data.chooseProfile(null, SessionUpdateReason.LOGIN);
    }

    @Override
    public List<Identifier> collectModules() {
        return new ArrayList<>(modules);
    }
}
