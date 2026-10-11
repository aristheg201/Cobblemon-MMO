package vn.svframe.fantasyhub.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillBarPresentationTest {
    private static int slots(String json) {return SkillBarPresentation.slots(JsonParser.parseString(json).getAsJsonObject()).size();}
    @Test void savedLoadoutDoesNotOpenCastingBar(){assertEquals(0,slots("{\"loadout\":{\"1\":\"BITE\"},\"loadoutSlots\":[1]}"));}
    @Test void serverToggleAcknowledgementOpensAndCloses(){
        assertEquals(1,slots("{\"casting\":{\"active\":true,\"mode\":\"SKILL_BAR\",\"slots\":[{\"inputSlot\":2,\"id\":\"BITE\"}]}}"));
        assertEquals(0,slots("{\"casting\":{\"active\":false,\"mode\":\"SKILL_BAR\",\"slots\":[{}]}}"));
    }
    @Test void otherServerCastingModesDoNotOpenThisBar(){assertEquals(0,slots("{\"casting\":{\"active\":true,\"mode\":\"KEY_COMBOS\",\"slots\":[{}]}}"));}
    @Test void missingServerSlotDataDoesNotGuessBindings(){assertEquals(0,slots("{\"casting\":{\"active\":true,\"mode\":\"SKILL_BAR\"}}"));}
}
