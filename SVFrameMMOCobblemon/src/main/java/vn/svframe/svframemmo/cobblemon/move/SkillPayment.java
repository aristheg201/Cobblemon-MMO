package vn.svframe.svframemmo.cobblemon.move;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/** One price is quoted and charged in one currency; no exchange rate is implied. */
public record SkillPayment(int id,String provider,String currency,BigDecimal price) {
    public static SkillPayment quote(int option,Double override,double defaultPrice) {
        if(option!=1&&option!=2)throw new IllegalArgumentException("Unknown skill payment option");
        BigDecimal amount=BigDecimal.valueOf(override==null?defaultPrice:override).stripTrailingZeros();
        BigDecimal minimum=BigDecimal.valueOf(option==1?100:500_000);
        if(amount.compareTo(minimum)<0||amount.scale()>0)throw new IllegalArgumentException("Skill price is below its currency minimum or is not whole");
        return new SkillPayment(option,option==1?"beconomy":"cobbledollars",option==1?"secondary":"cobbledollars",amount);
    }
    public static Double override(Map<String,Double> prices,String move,String skill) {
        if(prices==null)return null;
        String canonical=move.replaceAll("[^A-Za-z0-9]","").toLowerCase(Locale.ROOT);
        for(var entry:prices.entrySet()) {
            String key=entry.getKey();
            if(key.equalsIgnoreCase(move)||key.equalsIgnoreCase(skill)||key.replaceAll("[^A-Za-z0-9]","").equalsIgnoreCase(canonical))return entry.getValue();
        }
        return null;
    }
}
