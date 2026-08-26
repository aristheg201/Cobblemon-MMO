package io.lumine.mythic.lib.version;

import io.lumine.mythic.lib.version.wrapper.VersionWrapper;

import java.util.Arrays;

/** Fabric-native 1.21.1 server version descriptor retaining the 1.7.1 comparison API. */
public class ServerVersion {
    private static final int[] VERSION = {1, 21, 1};
    private static final ServerVersion INSTANCE = new ServerVersion(true);
    private final VersionWrapper wrapper = VersionWrapper.get();

    public ServerVersion(Class<?> ignored) { this(false); }
    public ServerVersion() { this(false); }
    private ServerVersion(boolean ignored) { }

    public void validateMappings() { }
    public boolean isPaper() { return false; }
    public boolean isAbove(int... version) { return isStrictlyHigher(version); }
    public boolean isUnder(int... version) { return compare(VERSION, version) < 0; }
    public String getCraftBukkitVersion() { return "fabric-1.21.1"; }
    public int getRevisionNumber() { return 1; }
    public int[] getBukkitVersion() { return VERSION.clone(); }
    public VersionWrapper getWrapper() { return wrapper; }
    @Override public String toString() { return "1.21.1-fabric"; }
    public static ServerVersion get() { return INSTANCE; }
    public String getRevision() { return "R1"; }
    public int[] toNumbers() { return VERSION.clone(); }
    public int[] getIntegers() { return VERSION.clone(); }
    public boolean isStrictlyHigher(int... version) { return compare(VERSION, version) > 0; }
    public boolean isBelowOrEqual(int... version) { return !isStrictlyHigher(version); }

    private static int compare(int[] left, int[] right) {
        if (right == null || right.length < 1 || right.length > 3) throw new IllegalArgumentException("Version must contain between 1 and 3 integers");
        int length = Math.min(3, Math.max(left.length, right.length));
        for (int i = 0; i < length; i++) {
            int a = i < left.length ? left[i] : 0;
            int b = i < right.length ? right[i] : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }
}
