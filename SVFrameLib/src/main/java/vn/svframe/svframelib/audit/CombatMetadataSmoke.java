package vn.svframe.svframelib.audit;

import vn.svframe.svframelib.damage.DamageMetadata;
import vn.svframe.svframelib.damage.DamageType;

import java.util.List;

/** Pure regression gate for the public native SVFrame combat metadata contract. */
public final class CombatMetadataSmoke {
    private CombatMetadataSmoke() { }

    public static void main(String[] args) {
        List<DamageType> types = DamageType.listFromConfig("SKILL,MAGIC");
        require(types.equals(List.of(DamageType.SKILL, DamageType.MAGIC)), "damage type parsing");

        DamageMetadata damage = new DamageMetadata(10d, types);
        damage.add(5d, List.of(DamageType.SKILL, DamageType.MAGIC));
        require(close(damage.getDamage(), 15d), "total typed damage");
        require(close(damage.getDamage(DamageType.SKILL), 15d), "skill packet aggregation");
        require(close(damage.getDamage(DamageType.MAGIC), 15d), "magic packet aggregation");

        DamageMetadata floor = new DamageMetadata(0d, List.of());
        require(close(floor.getDamage(), .01d), "minimal damage floor");
        require(floor.getPackets() == floor.getPackets(), "packet list must be the mutable backing list");
        System.out.println("COMBAT_TYPED_METADATA=PASS");
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-9; }
    private static void require(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
