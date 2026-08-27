package vn.svframe.svframelib.audit;

import vn.svframe.svframelib.fabric.BuiltinSkillOwnership;
import vn.svframe.svframelib.fabric.NativeDefaultSkillRuntime;

/** Regression gate: SVFrameLib owns and executes exactly 90 built-ins; SVFrameMMO owns three external IDs. */
public final class BuiltinOwnershipRuntimeSmoke {
    private BuiltinOwnershipRuntimeSmoke() { }

    public static void main(String[] args) {
        require(BuiltinSkillOwnership.nativeIds().size() == 90, "native ownership count");
        require(BuiltinSkillOwnership.externalProviderIds().size() == 3, "external ownership count");
        require(NativeDefaultSkillRuntime.ids().size() == 90, "native runtime count");
        for (String id : BuiltinSkillOwnership.nativeIds())
            require(NativeDefaultSkillRuntime.supports(id), "native ID missing from runtime: " + id);
        for (String id : BuiltinSkillOwnership.externalProviderIds()) {
            require(!BuiltinSkillOwnership.isNative(id), "external ID leaked into native ownership: " + id);
            require(!NativeDefaultSkillRuntime.supports(id), "external ID leaked into native runtime: " + id);
        }
        System.out.println("BUILTIN_OWNERSHIP_RUNTIME=PASS");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
