package vn.svframe.fantasyhub.client;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QaPresentationSnapshotTest {
    @Test void fullHunterDescriptionsCannotOverflowTheObservationPacketWhenFIsEnabled() {
        JsonObject casting=new JsonObject();casting.addProperty("active",true);casting.addProperty("mode","SKILL_BAR");JsonArray slots=new JsonArray();
        for(int i=0;i<7;i++){var slot=new JsonObject();slot.addProperty("id","CLS_HUNTER_HUNTER_"+i);slot.addProperty("inputSlot",i+1);slot.addProperty("cooldown",3.5);slot.addProperty("lore","original source description ".repeat(200));slots.add(slot);}
        casting.add("slots",slots);var compact=QaPresentationSnapshot.casting(casting);JsonObject packet=new JsonObject();packet.add("clientCasting",compact);packet.add("displayedSkillSlots",QaPresentationSnapshot.slots(slots));
        assertTrue(packet.toString().length()<3000);assertTrue(compact.get("active").getAsBoolean());assertEquals("SKILL_BAR",compact.get("mode").getAsString());assertEquals(7,compact.getAsJsonArray("slots").size());
        assertEquals("CLS_HUNTER_HUNTER_6",compact.getAsJsonArray("slots").get(6).getAsJsonObject().get("id").getAsString());assertFalse(compact.getAsJsonArray("slots").get(0).getAsJsonObject().has("lore"));assertTrue(slots.get(0).getAsJsonObject().has("lore"));
    }
}
