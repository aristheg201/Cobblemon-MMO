package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CastingStateTest {
    private JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    @Test void dedicatedCastingPacketOpensBeforeTheCatalogArrives(){
        CastingState casting=new CastingState();casting.accept(json("{\"schema\":1,\"mana\":100,\"casting\":{\"active\":true,\"mode\":\"SKILL_BAR\",\"slots\":[{\"inputSlot\":2,\"id\":\"BITE\"}]}}"));
        JsonObject state=new JsonObject();casting.mergeInto(state);
        assertEquals(1,SkillBarPresentation.slots(state).size());assertEquals(100,state.get("mana").getAsInt());assertEquals(1,state.get("schema").getAsInt());
    }
    @Test void delayedCatalogCannotHideAnAcknowledgedFPress(){
        CastingState casting=new CastingState();casting.accept(json("{\"casting\":{\"active\":true,\"mode\":\"SKILL_BAR\",\"slots\":[{\"inputSlot\":3}]}}"));
        JsonObject state=json("{\"casting\":{\"active\":false},\"skills\":[{\"id\":\"BITE\"}]}");casting.mergeInto(state);
        assertEquals(1,SkillBarPresentation.slots(state).size());assertEquals(1,state.getAsJsonArray("skills").size());
    }
    @Test void secondFClosesAndReconnectDiscardsTheOldSession(){
        CastingState casting=new CastingState();casting.accept(json("{\"casting\":{\"active\":false,\"mode\":\"\",\"slots\":[]}}"));
        JsonObject state=new JsonObject();casting.mergeInto(state);assertEquals(0,SkillBarPresentation.slots(state).size());
        casting.reset();state=new JsonObject();casting.mergeInto(state);assertFalse(state.has("casting"));
    }
}
