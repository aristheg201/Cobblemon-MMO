package vn.svframe.svframelib.profile.handler;

import net.minecraft.util.Identifier;

import java.util.List;

/** Native Fabric profile handler contract. */
public interface ProfileHandler {
    void onStartup();
    List<Identifier> collectModules();
}
