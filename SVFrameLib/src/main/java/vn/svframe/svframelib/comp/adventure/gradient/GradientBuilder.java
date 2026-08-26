package vn.svframe.svframelib.comp.adventure.gradient;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Native equivalent of MythicLib 1.7.1 GradientBuilder. */
public class GradientBuilder {
    public static String rgbGradient(String str, Color from, Color to, double phase, Interpolator interpolator) {
        return rgbGradient(str, from, to, phase, interpolator, new ArrayList<>());
    }

    public static String rgbGradient(String str, Color from, Color to, double phase, Interpolator interpolator, List<String> decorations) {
        final double[] red = interpolator.interpolate(from.getRed(), to.getRed(), str.length());
        final double[] green = interpolator.interpolate(from.getGreen(), to.getGreen(), str.length());
        final double[] blue = interpolator.interpolate(from.getBlue(), to.getBlue(), str.length());
        final StringBuilder builder = new StringBuilder();
        int start = str.length() - (int) (str.length() * phase);
        String decoration = String.join("", decorations);
        int charIndex = 0;
        for (int i = start; i < str.length(); i++) {
            builder.append(hex(new Color((int) Math.round(red[i]), (int) Math.round(green[i]), (int) Math.round(blue[i]))))
                    .append(decoration).append(str.charAt(charIndex++));
        }
        for (int i = 0; i < start; i++) {
            builder.append(hex(new Color((int) Math.round(red[i]), (int) Math.round(green[i]), (int) Math.round(blue[i]))))
                    .append(decoration).append(str.charAt(charIndex++));
        }
        return builder.toString();
    }

    public static String multiRgbGradient(String str, Color[] colors, double[] portions, Interpolator interpolator, List<String> decorations) {
        final double[] p;
        if (portions == null) {
            p = new double[colors.length - 1];
            Arrays.fill(p, 1 / (double) p.length);
        } else p = portions;
        if (colors.length < 2) throw new IllegalArgumentException("At least two colors are required");
        if (p.length != colors.length - 1) throw new IllegalArgumentException("Portions must equal colors - 1");
        final StringBuilder builder = new StringBuilder();
        int stringIndex = 0;
        for (double portion : p) {
            final int length = (int) (portion * str.length());
            final String substring = str.substring(stringIndex, stringIndex + length);
            builder.append(rgbGradient(substring, colors[0], colors[1], 0d, interpolator, decorations));
            colors = Arrays.copyOfRange(colors, 1, colors.length);
            stringIndex += length;
        }
        if (stringIndex < str.length())
            builder.append(hex(colors[colors.length - 1])).append(String.join("", decorations)).append(str.substring(stringIndex));
        return builder.toString();
    }

    public static String multiRgbGradient(String str, Color[] colors, double phase, Interpolator interpolator, List<String> decorations) {
        Color[] c = new Color[colors.length];
        for (int i = 0; i < colors.length; i++)
            c[i] = colors[Math.floorMod(i + (int) (colors.length * phase), colors.length)];
        return multiRgbGradient(str, c, null, interpolator, decorations);
    }

    public static String hex(Color color) {
        String h = String.format("%06x", color.getRGB() & 0xffffff);
        StringBuilder out = new StringBuilder("§x");
        for (char ch : h.toCharArray()) out.append('§').append(ch);
        return out.toString();
    }
}
