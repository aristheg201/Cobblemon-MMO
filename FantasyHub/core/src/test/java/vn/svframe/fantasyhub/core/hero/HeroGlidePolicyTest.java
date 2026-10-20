package vn.svframe.fantasyhub.core.hero;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HeroGlidePolicyTest {
 @Test void fullJumpAscentIsPreserved(){for(double y:new double[]{.42,.33,.16,.01})assertFalse(HeroGlidePolicy.shouldGlide(true,true,false,false,false,y));}
 @Test void descendingPlayerCanStartAndSteerNativeGlide(){assertTrue(HeroGlidePolicy.shouldGlide(true,true,false,false,false,-.08));assertTrue(HeroGlidePolicy.shouldGlide(true,true,false,false,true,.1));}
 @Test void releaseLandingGearRemovalAndControlStopGlide(){assertFalse(HeroGlidePolicy.shouldGlide(true,false,false,false,true,-.2));assertFalse(HeroGlidePolicy.shouldGlide(true,true,true,false,true,-.2));assertFalse(HeroGlidePolicy.shouldGlide(false,true,false,false,true,-.2));assertFalse(HeroGlidePolicy.shouldGlide(true,true,false,true,true,-.2));}
}
