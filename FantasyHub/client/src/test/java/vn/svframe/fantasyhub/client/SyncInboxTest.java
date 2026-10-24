package vn.svframe.fantasyhub.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

class SyncInboxTest {
  @Test
  void catalogChunksAppendExactlyOnceAndGapsRequireResync(){
    var inbox=new SyncInbox();var state=new JsonObject();
    assertTrue(inbox.apply(state,1,1,1,"GUI","{\"pokemonShopPage\":{\"start\":0,\"rows\":[{\"id\":\"one\"}]}}",0,0));
    assertTrue(inbox.apply(state,1,2,2,"GUI","{\"pokemonShopPage\":{\"start\":1,\"rows\":[{\"id\":\"two\"}]}}",0,0));
    assertEquals(2,state.getAsJsonArray("pokemonShop").size());
    assertFalse(inbox.apply(state,1,2,2,"GUI","{}",0,0));assertEquals(2,state.getAsJsonArray("pokemonShop").size());
    assertFalse(inbox.apply(state,1,3,3,"GUI","{\"pokemonShopPage\":{\"start\":7,\"rows\":[]}}",0,0));
    assertTrue(inbox.resyncRequested());assertEquals(2,state.getAsJsonArray("pokemonShop").size());
  }

  @Test
  void rejectsOlderSessionAndDomainRevisionButAllowsIndependentDomains() {
    var i = new SyncInbox();
    var state = new JsonObject();
    assertTrue(i.apply(state, 2, 4, 10, "VITALS", "{\"health\":20}", 100, 100));
    assertFalse(i.apply(state, 2, 3, 11, "VITALS", "bad-json", 100, 100));
    assertTrue(i.apply(state, 2, 1, 9, "CLASS", "{\"classId\":\"THOR\"}", 100, 100));
    assertFalse(i.apply(state, 1, 50, 100, "CLASS", "bad-json", 100, 100));
    assertEquals(20, state.get("health").getAsInt());
    assertTrue(i.apply(state, 3, 1, 1, "CLASS", "{\"classId\":\"FLASH\"}", 100, 100));
    assertFalse(state.has("health"));
    i.reset();
    assertTrue(i.apply(state, 1, 1, 1, "CLASS", "{}", 100, 100));
  }

  @Test
  void countdownUsesOneAuthoritativeDeadlineInsteadOfRepeatedPackets() {
    var skill =
        JsonParser.parseString("{\"cooldownEnd\":5000,\"cooldownRemaining\":5}").getAsJsonObject();
    assertEquals(3, SkillBarPresentation.remaining(skill, 2000), 1e-8);
    assertEquals(0, SkillBarPresentation.remaining(skill, 6000));
  }

  @Test
  void olderCatalogCannotOverwriteNewerAuthoritativeWallet() {
    var i = new SyncInbox();
    var state = new JsonObject();
    i.apply(state, 1, 2, 2, "COMMERCE", "{\"shopStatus\":{\"beastBalance\":99}}", 0, 0);
    i.apply(
        state, 1, 1, 1, "BUNDLES", "{\"fantasyShop\":{\"beastBalance\":100,\"offers\":[]}}", 0, 0);
    assertEquals(99, state.getAsJsonObject("fantasyShop").get("beastBalance").getAsInt());
  }
  @Test void cachedMenuCannotRestoreAnExpiredDeadlineOverNewCombatState() {
    var inbox=new SyncInbox();var state=new JsonObject();
    inbox.apply(state,1,3,5,"COMBAT","{\"combatState\":{\"active\":true,\"slots\":[{\"id\":\"THOR\",\"cooldownEnd\":6000}]}}",0,0);
    inbox.apply(state,1,1,1,"GUI","{\"skills\":[{\"id\":\"THOR\",\"cooldownEnd\":0}]}",0,0);
    assertEquals(6000,state.getAsJsonArray("skills").get(0).getAsJsonObject().get("cooldownEnd").getAsLong());
  }

}
