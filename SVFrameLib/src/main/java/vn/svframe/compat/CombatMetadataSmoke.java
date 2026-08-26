package vn.svframe.compat;

import vn.svframe.mythiclibfabric.runtime.DamageType;
import vn.svframe.mythiclibfabric.runtime.NativeDamageMetadata;

import java.util.List;

/** Pure regression gate for typed/elemental packet composition used by the native combat bridge. */
public final class CombatMetadataSmoke {
    private CombatMetadataSmoke() { }

    public static void main(String[] args) {
        List<DamageType> types = DamageType.listFromConfig("SKILL,MAGIC");
        require(types.equals(List.of(DamageType.SKILL, DamageType.MAGIC)), "damage type parsing");

        NativeDamageMetadata damage = new NativeDamageMetadata(10d, types);
        damage.add(5d, "FIRE", List.of(DamageType.SKILL, DamageType.MAGIC));
        require(close(damage.damage(), 15d), "total typed damage");
        require(close(damage.damage(DamageType.SKILL), 15d), "skill packet aggregation");
        require(close(damage.damage(DamageType.MAGIC), 15d), "magic packet aggregation");
        require(close(damage.damage("FIRE"), 5d), "element packet aggregation");
        System.out.println("COMBAT_TYPED_METADATA=PASS");
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-9; }
    private static void require(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
