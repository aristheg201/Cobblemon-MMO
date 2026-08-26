package io.lumine.mythic.lib.comp.profile;

import io.lumine.mythic.lib.profile.handler.LegacyProfileHandler;
import io.lumine.mythic.lib.profile.handler.NoProfileHandler;
import io.lumine.mythic.lib.profile.handler.ProfileHandler;
import io.lumine.mythic.lib.profile.handler.ProxyProfileHandler;

import java.util.function.Supplier;

/** Exact 1.7.1 profile-mode surface backed by native Fabric handlers. */
public enum ProfileMode {
    LEGACY(LegacyProfileHandler::new),
    PROXY(ProxyProfileHandler::new),
    NONE(NoProfileHandler::new);

    private final Supplier<ProfileHandler> profileHandlerSupplier;

    ProfileMode(Supplier<ProfileHandler> profileHandlerSupplier) {
        this.profileHandlerSupplier = profileHandlerSupplier;
    }

    public ProfileHandler newProfileHandler() {
        return profileHandlerSupplier.get();
    }
}
