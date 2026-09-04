package vn.svframe.svframelib.util.configobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonWrapperTest {
    private static final class ExposedJsonWrapper extends JsonWrapper {
        private ExposedJsonWrapper(String line) { super(line); }
    }

    @Test
    void parsesSemicolonAndCommaDelimitedArguments() {
        JsonWrapper config = new ExposedJsonWrapper("stat{stat=\"ADDITIONAL_EXPERIENCE\";amount=2;type=\"FLAT\",enabled=true}");
        assertEquals("stat", config.getKey());
        assertEquals("ADDITIONAL_EXPERIENCE", config.getString("stat"));
        assertEquals(2, config.getInt("amount"));
        assertEquals("FLAT", config.getString("type"));
        assertTrue(config.getBoolean("enabled"));
    }

    @Test
    void preservesQuotedAndNestedDelimiters() {
        JsonWrapper config = new ExposedJsonWrapper("demo{text=\"a;b,c\";nested={x=1;y=2};flag}");
        assertEquals("a;b,c", config.getString("text"));
        assertEquals("{x=1;y=2}", config.getString("nested"));
        assertTrue(config.getBoolean("flag"));
    }
}
