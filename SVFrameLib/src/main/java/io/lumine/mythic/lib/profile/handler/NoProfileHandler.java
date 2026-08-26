package io.lumine.mythic.lib.profile.handler;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.profile.SessionUpdateReason;
import net.minecraft.util.Identifier;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;

import java.util.ArrayList;
import java.util.List;

/** Native Fabric equivalent of MythicLib 1.7.1's no-profile handler. */
public final class NoProfileHandler implements ProfileHandler {
    private final List<Identifier> modules;

    public NoProfileHandler() {
        this.modules = MythicLib.inst().getMMOPlugins().stream()
                .filter(MMOPlugin::hasData)
                .map(plugin -> Identifier.of(plugin.getNamespacedKey(), "plugin"))
                .toList();
    }

    @Override
    public void onStartup() {
        MythicLibFabricMod.schedule(20, () -> MMOPlayerData.getLoaded().forEach(data -> {
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
