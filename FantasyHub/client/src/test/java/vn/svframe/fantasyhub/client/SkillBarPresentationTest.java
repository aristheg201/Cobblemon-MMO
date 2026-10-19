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
    @Test void sevenSlotsStayCenteredAboveTheReservedResourceRowsAcrossGuiScales(){
        for(int[] screen:new int[][]{{1280,720},{640,360},{426,240},{320,180}}){
            var row=SkillBarPresentation.layout(screen[0],screen[1],7,screen[1]-49);
            assertTrue(row.x()>=0);assertEquals(screen[0]/2,row.x()+row.width()/2);
            assertTrue(row.y()+row.slotHeight()<=screen[1]-77);
        }
    }
    @Test void cooldownWipeClampsAndSubsecondTimeRemainsReadable(){
        var skill=JsonParser.parseString("{\"cooldownRemaining\":2,\"parameters\":{\"cooldown\":10}}").getAsJsonObject();
        assertEquals(.2,SkillBarPresentation.cooldownFraction(skill),.0001);
        skill.addProperty("cooldownRemaining",20);assertEquals(1,SkillBarPresentation.cooldownFraction(skill));
        skill.addProperty("cooldownRemaining",-2);assertEquals(0,SkillBarPresentation.cooldownFraction(skill));
        assertEquals("0.8",SkillBarPresentation.cooldownLabel(.75));assertEquals("",SkillBarPresentation.cooldownLabel(0));
    }
    @Test void largeMinecraftGuiScalesDoNotEnlargeTheActionBarIntoTheCrosshair(){
        assertEquals(1,SkillBarPresentation.renderScale(1,1));
        assertEquals(1,SkillBarPresentation.renderScale(2,1));
        assertEquals(2f/3f,SkillBarPresentation.renderScale(3,1),.0001);
        assertEquals(.5f,SkillBarPresentation.renderScale(4,1));
        assertEquals(.5f,SkillBarPresentation.renderScale(2,.5f));
    }
}
