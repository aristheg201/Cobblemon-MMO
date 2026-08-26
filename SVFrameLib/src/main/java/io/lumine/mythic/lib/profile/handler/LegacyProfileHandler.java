package io.lumine.mythic.lib.profile.handler;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.module.MMOPlugin;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric profile bridge for legacy profile mode. Bukkit's ProfileAPI service has
 * no native Fabric equivalent, so the native MMO module registry is authoritative.
 */
public final class LegacyProfileHandler implements ProfileHandler {
    @Override public void onStartup() { }

    @Override
    public List<Identifier> collectModules() {
        List<Identifier> modules = new ArrayList<>();
        for (MMOPlugin plugin : MythicLib.inst().getMMOPlugins())
            if (plugin.hasData()) modules.add(Identifier.of(plugin.getNamespacedKey(), "plugin"));
        return modules;
    }
}
