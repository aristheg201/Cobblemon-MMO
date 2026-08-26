package vn.svframe.svframelib.comp.adventure.resolver.implementation;

import vn.svframe.svframelib.comp.adventure.argument.AdventureArgumentQueue;
import vn.svframe.svframelib.comp.adventure.resolver.AdventureTagResolver;
import vn.svframe.svframelib.util.AdventureUtils;
import vn.svframe.svframelib.comp.adventure.gradient.GradientBuilder;
import java.awt.Color;

public class AdventureColorResolver implements AdventureTagResolver {
    @Override public String resolve(String src, AdventureArgumentQueue arguments) {
        if (!arguments.hasNext()) return null;
        String raw=arguments.pop().value();
        var formatting=AdventureUtils.getByName(raw);
        if (formatting.isPresent()) return formatting.get().toString();
        try { String s=raw.startsWith("#")?raw.substring(1):raw; return GradientBuilder.hex(new Color(Integer.parseInt(s,16)&0xffffff)); }
        catch (RuntimeException ignored) { return null; }
    }
}
