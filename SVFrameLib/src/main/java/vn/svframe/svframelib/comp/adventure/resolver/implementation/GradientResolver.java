package vn.svframe.svframelib.comp.adventure.resolver.implementation;

import vn.svframe.svframelib.comp.adventure.argument.AdventureArgumentQueue;
import vn.svframe.svframelib.comp.adventure.gradient.GradientBuilder;
import vn.svframe.svframelib.comp.adventure.gradient.Interpolator;
import vn.svframe.svframelib.comp.adventure.resolver.ContextTagResolver;
import vn.svframe.svframelib.util.AdventureUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class GradientResolver implements ContextTagResolver {
    @Override
    public String resolve(String src, AdventureArgumentQueue argsQueue, String context, List<String> decorations) {
        if (!argsQueue.hasNext())
            return GradientBuilder.rgbGradient(context, Color.WHITE, Color.BLACK, 0, Interpolator.LINEAR, decorations);
        List<String> args = new ArrayList<>();
        while (argsQueue.hasNext()) args.add(argsQueue.pop().value());
        double phase = getPhase(args);
        if (args.size() > 2)
            return GradientBuilder.multiRgbGradient(context, args.stream().map(AdventureUtils::color).toArray(Color[]::new), phase, Interpolator.LINEAR, decorations);
        final Color c1 = AdventureUtils.color(args.get(0));
        if (args.size() == 1)
            return GradientBuilder.rgbGradient(context, c1, Color.BLACK, phase, Interpolator.LINEAR, decorations);
        return GradientBuilder.rgbGradient(context, c1, AdventureUtils.color(args.get(1)), phase, Interpolator.LINEAR, decorations);
    }

    private double getPhase(List<String> args) {
        String lastArg = args.get(args.size() - 1);
        try {
            double phase = Double.parseDouble(lastArg);
            args.remove(args.size() - 1);
            return phase;
        } catch (NumberFormatException e) {
            return 1d;
        }
    }
}
