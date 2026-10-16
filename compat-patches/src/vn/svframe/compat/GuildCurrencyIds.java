package vn.svframe.compat;

/** The user identified Beastcoin as BEconomy:secondary. No amounts or exchange rates change. */
public final class GuildCurrencyIds {
    private GuildCurrencyIds() {}
    public static String resolve(String configured) {
        String candidate=configured;
        if (candidate.regionMatches(true,0,"BEconomy:",0,9)) candidate=candidate.substring(9);
        if (!candidate.equalsIgnoreCase("beastcoin")) return candidate;
        try {
            Class<?> owner=Class.forName("org.krripe.beconomy.api.BEconomy");
            Object api=owner.getMethod("getAPI").invoke(owner.getField("INSTANCE").get(null));
            var exists=Class.forName("org.krripe.beconomy.api.EconomyAPI").getMethod("currencyExists",String.class);
            if (!Boolean.TRUE.equals(exists.invoke(api,candidate)) && Boolean.TRUE.equals(exists.invoke(api,"secondary"))) {
                System.getLogger("GuildProgression-Currency").log(System.Logger.Level.INFO,
                    "Resolved configured Beastcoin display name to BEconomy API currency secondary.");
                return "secondary";
            }
        } catch(ReflectiveOperationException unavailable) {
            // Keep the original constructor's exact missing-integration/error behavior.
        }
        return candidate;
    }
}
