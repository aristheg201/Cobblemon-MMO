package vn.svframe.svframelib.comp.adventure.resolver.implementation;

import vn.svframe.svframelib.comp.adventure.argument.AdventureArgumentQueue;
import vn.svframe.svframelib.comp.adventure.gradient.GradientBuilder;
import vn.svframe.svframelib.comp.adventure.resolver.AdventureTagResolver;
import java.awt.Color;

public class HexColorResolver implements AdventureTagResolver {
    @Override public String resolve(String src, AdventureArgumentQueue arguments) {
        String raw = arguments.hasNext() ? arguments.pop().value() : (src != null && src.startsWith("#") ? src : null);
        if (raw == null) return null;
        try { String s=raw.startsWith("#")?raw.substring(1):raw; return GradientBuilder.hex(new Color(Integer.parseInt(s,16)&0xffffff)); }
        catch (RuntimeException ignored) { return null; }
    }
}
