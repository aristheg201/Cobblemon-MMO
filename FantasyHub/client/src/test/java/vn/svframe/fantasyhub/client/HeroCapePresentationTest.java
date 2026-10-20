package vn.svframe.fantasyhub.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroCapePresentationTest {
 @Test void batmanBackAlwaysOverridesPersonalCape(){assertEquals("batman",HeroCapePresentation.select(true,true,false,false,""));assertEquals("batman",HeroCapePresentation.select(false,true,false,false,""));assertEquals("",HeroCapePresentation.select(true,false,true,false,"thor"));}

  @Test void personalSkinCapeTakesPriorityOverEveryFallback(){
    for(String suit:new String[]{"thor","sorcerer_supreme","batman",""})assertEquals("",HeroCapePresentation.fallback(true,true,true,suit));
  }
  @Test void noPersonalCapeUsesEquippedCapeOrSuitFallback(){
    assertEquals("thor",HeroCapePresentation.fallback(false,true,false,""));
    assertEquals("sorcerer_supreme",HeroCapePresentation.fallback(false,false,true,""));
    assertEquals("thor",HeroCapePresentation.fallback(false,false,false,"thor"));
    assertEquals("sorcerer_supreme",HeroCapePresentation.fallback(false,false,false,"sorcerer_supreme"));
    assertEquals("",HeroCapePresentation.fallback(false,false,false,"spider_man"));
  }
}
