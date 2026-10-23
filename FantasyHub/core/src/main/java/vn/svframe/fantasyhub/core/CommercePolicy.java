package vn.svframe.fantasyhub.core;
import java.math.BigDecimal;
/** Explicit BEconomy prices; no conversion and no implicit payment fallback. */
public final class CommercePolicy {
 private CommercePolicy(){}
 public static String currency(int method){return switch(method){case 1->"beconomy:secondary";case 2->"beconomy:primary";default->throw new IllegalArgumentException("Choose BeastCoin or HunterCoin");};}
 public static BigDecimal switchCharge(boolean owned,boolean cooling,boolean bypass,BigDecimal price){
  if(!owned)throw new IllegalArgumentException("Class not owned");
  if(!cooling)return BigDecimal.ZERO;
  if(!bypass)throw new IllegalArgumentException("Class switch cooldown");
  positiveWhole(price);return price;
 }
 public static void positiveWhole(BigDecimal p){if(p==null||p.signum()<=0||p.stripTrailingZeros().scale()>0)throw new IllegalArgumentException("Price must be a positive whole amount");}
 public static void validateItemPrices(BigDecimal beast,BigDecimal hunter){positiveWhole(beast);positiveWhole(hunter);if(beast.compareTo(BigDecimal.valueOf(500))<0||beast.compareTo(BigDecimal.valueOf(3000))>0||hunter.compareTo(BigDecimal.valueOf(2))<0)throw new IllegalArgumentException("Item shop requires 500–3000 BC and at least 2 whole HC");}
}
