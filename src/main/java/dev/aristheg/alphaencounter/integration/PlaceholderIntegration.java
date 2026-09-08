package dev.aristheg.alphaencounter.integration;

import dev.aristheg.alphaencounter.text.TextContext;
import net.minecraft.text.Text;

public interface PlaceholderIntegration {
    PlaceholderIntegration NONE = new PlaceholderIntegration() {
        public Text parse(Text input, TextContext context) { return input; }
        public boolean available() { return false; }
        public String status() { return "not installed"; }
    };
    Text parse(Text input, TextContext context);
    boolean available();
    String status();
}
