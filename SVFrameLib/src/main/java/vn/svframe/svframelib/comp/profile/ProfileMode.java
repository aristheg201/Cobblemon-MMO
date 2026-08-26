package vn.svframe.svframelib.comp.profile;

import vn.svframe.svframelib.profile.handler.LegacyProfileHandler;
import vn.svframe.svframelib.profile.handler.NoProfileHandler;
import vn.svframe.svframelib.profile.handler.ProfileHandler;
import vn.svframe.svframelib.profile.handler.ProxyProfileHandler;

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
