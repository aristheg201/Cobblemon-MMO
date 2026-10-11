package vn.svframe.fantasyhub.core;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Complete source data must exist before any framework initializes its registries. */
public final class SourcePackPreLaunch implements PreLaunchEntrypoint {
    @Override public void onPreLaunch(){try{SourcePackInstaller.install();}catch(java.io.IOException failure){throw new IllegalStateException("Cannot install the authored Fantasy Hub source pack",failure);}}
}
