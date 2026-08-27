package vn.svframe.compat;

import vn.svframe.mythiclibfabric.BuiltinSkillOwnership;

/** Regression gate: SVFrameLib owns exactly 90 built-ins and SVFrameMMO owns exactly three external IDs. */
public final class BuiltinOwnershipRuntimeSmoke {
    private BuiltinOwnershipRuntimeSmoke() { }

    public static void main(String[] args) {
        require(BuiltinSkillOwnership.nativeIds().size() == 90, "native ownership count");
        require(BuiltinSkillOwnership.externalProviderIds().size() == 3, "external ownership count");
        for (String id : BuiltinSkillOwnership.externalProviderIds())
            require(!BuiltinSkillOwnership.isNative(id), "external ID leaked into native ownership: " + id);
        System.out.println("BUILTIN_OWNERSHIP_RUNTIME=PASS");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
