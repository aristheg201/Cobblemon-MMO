package vn.svframe.svframemmo.cobblemon.move;

import java.util.Set;

/** Purchase coverage, independent of the complete registry needed by fusion and existing saves. */
public final class PlayerMoveCoverage {
    private static final Set<String> SINGLE_HIT=Set.of("tackle","pound","scratch","cut","strength","slam","megapunch","megakick","visegrip","wingattack");
    private static final Set<String> HALF_HEAL=Set.of("recover","slackoff","softboiled","milkdrink","healorder");
    private PlayerMoveCoverage(){}
    public static boolean purchasable(String move){String id=MoveSemanticRegistry.id(move);return SINGLE_HIT.contains(id)||HALF_HEAL.contains(id);}
    public static Set<String> purchaseAdapters(){var ids=new java.util.TreeSet<String>(SINGLE_HIT);ids.addAll(HALF_HEAL);return java.util.Collections.unmodifiableSet(ids);}
    public static String reason(String move){return purchasable(move)?"native-overworld-single-hit-or-half-heal":"requires-move-specific-adapter-and-runtime-validation";}
}
