package vn.svframe.fantasyhub.client;
final class HeroEffectPolicy {
 static final int MAX_CUES=128;
 static boolean retained(String phase){return java.util.Set.of("CAST","BEAM","WEAPON_THROW","AXE","TETHER","PROJECTILE","TRAIL","ZONE","IMPACT","HIT").contains(phase);}
}
