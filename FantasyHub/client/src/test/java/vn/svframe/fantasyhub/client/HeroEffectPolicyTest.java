package vn.svframe.fantasyhub.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HeroEffectPolicyTest {
 @Test void collisionsAndFlightRemainVisibleAfterPacketTick(){for(String phase:new String[]{"HIT","IMPACT","PROJECTILE","TRAIL","WEAPON_THROW"})assertTrue(HeroEffectPolicy.retained(phase));assertFalse(HeroEffectPolicy.retained("untrusted"));}
}
