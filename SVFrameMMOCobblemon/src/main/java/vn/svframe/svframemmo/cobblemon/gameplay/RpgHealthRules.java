package vn.svframe.svframemmo.cobblemon.gameplay;
public final class RpgHealthRules {
 private RpgHealthRules(){}
 public static double maximum(double baseHp,int level,boolean alpha,double alphaMultiplier,double cap){if(!Double.isFinite(baseHp)||baseHp<1||!Double.isFinite(alphaMultiplier)||alphaMultiplier<1||!Double.isFinite(cap)||cap<1)throw new IllegalArgumentException("Invalid RPG HP policy");return Math.min(cap,(baseHp*3+24+Math.max(1,level)*3)*(alpha?alphaMultiplier:1));}
}
