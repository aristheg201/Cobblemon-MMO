package vn.svframe.svframelib.comp.adventure.resolver.implementation;

import vn.svframe.svframelib.comp.adventure.argument.AdventureArgumentQueue;
import vn.svframe.svframelib.comp.adventure.gradient.GradientBuilder;
import vn.svframe.svframelib.comp.adventure.resolver.ContextTagResolver;
import vn.svframe.svframelib.util.AdventureUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class TransitionResolver implements ContextTagResolver {
    @Override
    public String resolve(String src, AdventureArgumentQueue argumentQueue, String context, List<String> decorations) {
        List<String> args = new ArrayList<>();
        while (argumentQueue.hasNext()) args.add(argumentQueue.pop().value());
        if (args.size() < 3) return null;
        double phase = getPhase(args);
        if (phase < 0 || phase > 1) return null;
        List<Color> colors = args.stream().map(AdventureUtils::color).toList();
        if (colors.size() < 2) return null;
        return GradientBuilder.hex(transition(phase, colors)) + String.join("", decorations) + context;
    }

    private Color transition(double phase, List<Color> colors) {
        if (colors.size() == 1) return colors.get(0);
        if (phase == 1) return colors.get(colors.size() - 1);
        double step = 1d / (colors.size() - 1);
        int index = (int) (phase / step);
        double localPhase = (phase - (index * step)) / step;
        return interpolate(colors.get(index), colors.get(index + 1), localPhase);
    }

    private Color interpolate(Color c1, Color c2, double phase) {
        return new Color(
                (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * phase),
                (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * phase),
                (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * phase),
                (int) (c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * phase));
    }

    private double getPhase(List<String> args) {
        String lastArg = args.get(args.size() - 1);
        try {
            double phase = Double.parseDouble(lastArg);
            args.remove(args.size() - 1);
            return phase;
        } catch (NumberFormatException e) { return 1d; }
    }
}
