package vn.svframe.fantasyhub.core;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Prices explicitly select secondary (beastcoin) or primary (huntercoin); they do not inherit the MMO's general currency. */
final class ClassPricing {
    record Price(BigDecimal amount, String currency) {
        String label() { return currency.equals("beconomy:secondary")?"beastcoin":"huntercoin"; }
    }
    private final Price ordinary;
    private final Price premium;
    private final Map<String, Price> overrides;
    private ClassPricing(Price ordinary, Price premium, Map<String, Price> overrides) {
        this.ordinary=ordinary;this.premium=premium;this.overrides=Map.copyOf(overrides);
    }
    static ClassPricing parse(Map<String,Object> config) {
        String beast=String.valueOf(config.getOrDefault("currency","beconomy:secondary"));
        String hunter=String.valueOf(config.getOrDefault("premium-currency","beconomy:primary"));
        if(!beast.equalsIgnoreCase("beconomy:secondary")||!hunter.equalsIgnoreCase("beconomy:primary"))
            throw new IllegalArgumentException("Ordinary classes use BEconomy secondary (beastcoin); premium classes use primary (huntercoin)");
        Price ordinary=new Price(ordinary(config.getOrDefault("fee",3000)),beast.toLowerCase(Locale.ROOT));
        BigDecimal premiumAmount=new BigDecimal(String.valueOf(config.getOrDefault("premium-fee",2)));
        if(premiumAmount.compareTo(BigDecimal.valueOf(2))!=0)throw new IllegalArgumentException("Dragonborn and Death Knight cost exactly 2 huntercoin");
        Price premium=new Price(premiumAmount,hunter.toLowerCase(Locale.ROOT));
        Map<String,Price> overrides=new LinkedHashMap<>();
        if(config.get("class-prices") instanceof Map<?,?> prices)prices.forEach((id,amount)->{
            String key=String.valueOf(id).toUpperCase(Locale.ROOT);
            if(isPremium(key)) {
                if(new BigDecimal(String.valueOf(amount)).compareTo(premiumAmount)!=0)throw new IllegalArgumentException(key+" costs 2 huntercoin");
            } else overrides.put(key,new Price(ordinary(amount),ordinary.currency()));
        });
        return new ClassPricing(ordinary,premium,overrides);
    }
    Price price(String id) { return isPremium(id)?premium:overrides.getOrDefault(id.toUpperCase(Locale.ROOT),ordinary); }
    private static boolean isPremium(String id) { return "DRAGONBORN".equalsIgnoreCase(id)||"DEATH_KNIGHT".equalsIgnoreCase(id); }
    private static BigDecimal ordinary(Object raw) {
        BigDecimal amount=new BigDecimal(String.valueOf(raw));
        if(amount.compareTo(BigDecimal.valueOf(1000))<0||amount.compareTo(BigDecimal.valueOf(5000))>0||amount.stripTrailingZeros().scale()>0)
            throw new IllegalArgumentException("Ordinary class prices must be whole beastcoin amounts from 1000 to 5000");
        return amount;
    }
}
