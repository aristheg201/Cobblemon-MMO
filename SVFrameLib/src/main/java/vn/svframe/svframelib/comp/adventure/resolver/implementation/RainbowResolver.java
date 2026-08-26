package vn.svframe.svframelib.comp.adventure.resolver.implementation;

import vn.svframe.svframelib.comp.adventure.argument.AdventureArgument;
import vn.svframe.svframelib.comp.adventure.argument.AdventureArgumentQueue;
import vn.svframe.svframelib.comp.adventure.gradient.GradientBuilder;
import vn.svframe.svframelib.comp.adventure.gradient.Interpolator;
import vn.svframe.svframelib.comp.adventure.resolver.ContextTagResolver;
import java.awt.Color;
import java.util.List;

public class RainbowResolver implements ContextTagResolver {
    private static final Color[] COLORS = {
            new Color(243, 138, 50), new Color(255, 255, 85), new Color(82, 255, 56),
            new Color(62, 136, 252), new Color(248, 54, 126), new Color(240, 64, 70)
    };

    @Override
    public String resolve(String src, AdventureArgumentQueue argsQueue, String context, List<String> decorations) {
        if (!argsQueue.hasNext())
            return GradientBuilder.multiRgbGradient(context, COLORS, 0, Interpolator.LINEAR, decorations);
        AdventureArgument argument = argsQueue.pop();
        if (argument.asInt().isPresent())
            return GradientBuilder.multiRgbGradient(context, COLORS, 1 - argument.asInt().getAsInt(), Interpolator.LINEAR, decorations);
        if (argument.value().matches("![0-9]+")) {
            try {
                int phase = Integer.parseInt(argument.value().substring(1));
                return GradientBuilder.multiRgbGradient(context, COLORS, 1 - phase, Interpolator.LINEAR, decorations);
            } catch (NumberFormatException e) { return null; }
        }
        if (argument.value().equals("!"))
            return GradientBuilder.multiRgbGradient(context, COLORS, 1, Interpolator.LINEAR, decorations);
        return null;
    }
}
