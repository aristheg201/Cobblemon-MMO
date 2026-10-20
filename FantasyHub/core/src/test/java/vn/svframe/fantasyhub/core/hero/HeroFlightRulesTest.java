package vn.svframe.fantasyhub.core.hero;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroFlightRulesTest {
  private static final Map<String,String> THOR=Map.of("CHEST","HERO_THOR_CHESTPLATE","LEGS","HERO_THOR_LEGGINGS","FEET","HERO_THOR_BOOTS");
  @Test void thorNeedsHisClassCapeAndEveryExistingSuitPiece() throws Exception {
    var rules=HeroFlightRules.defaults();
    assertTrue(rules.allows("THOR","fantasyhub:thor_cape",THOR));
    assertFalse(rules.allows("IRON_MAN","fantasyhub:thor_cape",THOR));
    assertFalse(rules.allows("THOR","",THOR));
    assertFalse(rules.allows("THOR","fantasyhub:cloak_of_levitation",THOR));
    for(String slot:THOR.keySet()){
      var incomplete=new HashMap<>(THOR);incomplete.remove(slot);assertFalse(rules.allows("THOR","fantasyhub:thor_cape",incomplete));
      incomplete.put(slot,"HERO_BATMAN_"+slot);assertFalse(rules.allows("THOR","fantasyhub:thor_cape",incomplete));
    }
  }
  @Test void sorcererOnlyNeedsHisClassAndHisBackCape() throws Exception {
    var rules=HeroFlightRules.defaults();
    assertTrue(rules.allows("SORCERER_SUPREME","fantasyhub:cloak_of_levitation",Map.of()));
    assertFalse(rules.allows("THOR","fantasyhub:cloak_of_levitation",Map.of()));
    assertFalse(rules.allows("SORCERER_SUPREME","fantasyhub:thor_cape",THOR));
    assertEquals(0,rules.rule("SORCERER_SUPREME").upkeepPerSecond());
    assertEquals(0,rules.rule("THOR").upkeepPerSecond());
  }
  @Test void unsafeFlightConfigurationIsRejected() throws Exception {
    var json=com.google.gson.JsonParser.parseString(new String(getClass().getResourceAsStream("/default/hero-flight.json").readAllBytes())).getAsJsonObject();
    json.getAsJsonObject("rules").getAsJsonObject("THOR").addProperty("speed",Double.NaN);
    assertThrows(IllegalArgumentException.class,()->HeroFlightRules.parse(json));
  }
  @Test void leaseRestoresOriginalPermissionsAndNeverSavesGrantedFlight(){
    var normal=new HeroFlightLease.Abilities(false,false,.05f);var granted=new HeroFlightLease.Abilities(true,true,.06f);
    var lease=new HeroFlightLease();lease.acquire(normal);lease.acquire(granted);
    assertEquals(normal,lease.saved(granted));assertEquals(normal,lease.release(granted,false));assertFalse(lease.owned());
    assertEquals(granted,lease.release(granted,false));
  }
  @Test void leasePreservesExternalFlightAndCreativeTransitions(){
    var external=new HeroFlightLease.Abilities(true,false,.09f);var flying=new HeroFlightLease.Abilities(true,true,.06f);
    var lease=new HeroFlightLease();lease.acquire(external);assertEquals(external,lease.release(flying,false));
    lease.acquire(new HeroFlightLease.Abilities(false,false,.05f));assertEquals(flying,lease.release(flying,true));
  }
  @Test void onlyFunctionalBackSlotsQualify(){
    for(String path:List.of("back","chest/back/0","body/back/1"))assertTrue(vn.svframe.fantasyhub.protocol.BackEquipment.isBackSlot(path));
    for(String path:List.of("mainhand","chest/cape/0","legs/belt/0","backpack","cosmetic/back/0"))assertFalse(vn.svframe.fantasyhub.protocol.BackEquipment.isBackSlot(path));
  }
  @Test void freeUpkeepDoesNotAttemptAResourceMutation(){
    assertTrue(HeroPassives.spend(null,0));
  }
}
