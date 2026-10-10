package vn.svframe.svframelib.fabric.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePlaceholderRegistryTest {
    @Test void percentAndAngleSyntaxReachTheSameProviderWithoutEatingModuloOrUnknownAddons() {
        NativePlaceholderRegistry.register("qa_profile", (id, argument) -> switch(argument) {
            case "health" -> "30";
            case "max_health" -> "60";
            default -> "";
        });
        try {
            assertEquals("30 / 60", NativePlaceholderRegistry.parse(null, "%qa_profile_health% / %qa_profile_max_health%"));
            assertEquals("30 / 60", NativePlaceholderRegistry.parse(null, "<qa_profile.health> / <qa_profile:max_health>"));
            assertEquals("5 % 2 + %unknownaddon_value%", NativePlaceholderRegistry.parse(null, "5 % 2 + %unknownaddon_value%"));
        } finally { NativePlaceholderRegistry.unregister("qa_profile"); }
    }
}
