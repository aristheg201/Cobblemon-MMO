package vn.svframe.fantasyhub.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperiencePresentationTest {
    @Test void onlyExplicitServerMmoPreferenceReplacesTheVanillaExperienceHud() {
        for(String state:new String[]{"{}","{\"experienceDisplay\":\"VANILLA\"}","{\"experienceDisplay\":\"unknown\"}","{\"experienceDisplay\":null}","{\"experienceDisplay\":true}","{\"experienceDisplay\":{}}"})
            assertFalse(ExperiencePresentation.rpg(JsonParser.parseString(state).getAsJsonObject()),state);
        assertTrue(ExperiencePresentation.rpg(JsonParser.parseString("{\"experienceDisplay\":\"MMO\"}").getAsJsonObject()));
    }
}
