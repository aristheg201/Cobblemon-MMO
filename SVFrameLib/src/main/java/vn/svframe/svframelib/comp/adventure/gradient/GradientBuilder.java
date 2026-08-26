package vn.svframe.svframelib.comp.adventure.gradient;

import java.awt.Color;
import java.util.List;

public class GradientBuilder {
    public static String rgbGradient(String text, Color start, Color end, double phase, Interpolator interpolator) {
        return rgbGradient(text,start,end,phase,interpolator,List.of());
    }

    public static String rgbGradient(String text, Color start, Color end, double phase, Interpolator interpolator, List<String> decorations) {
        return multiRgbGradient(text,new Color[]{start,end},phase,interpolator,decorations);
    }

    public static String multiRgbGradient(String text, Color[] colors, double[] positions, Interpolator interpolator, List<String> decorations) {
        if (colors == null || colors.length < 2) return text == null ? "" : text;
        if (positions == null || positions.length != colors.length) throw new IllegalArgumentException("positions must match colors");
        String src = text == null ? "" : text;
        int visible = (int) src.codePoints().filter(cp -> cp != '\n' && cp != '\r').count();
        if (visible == 0) return src;
        List<Integer> cps = src.codePoints().boxed().toList();
        StringBuilder out = new StringBuilder(src.length()*8);
        int ordinal = 0;
        for (int cp : cps) {
            if (cp == '\n' || cp == '\r') { out.appendCodePoint(cp); continue; }
            double t = visible <= 1 ? 0d : ordinal++/(double)(visible-1);
            int segment = 0;
            while (segment+1 < positions.length-1 && t > positions[segment+1]) segment++;
            double a = positions[segment], b = positions[segment+1];
            double local = b <= a ? 0d : Math.max(0d,Math.min(1d,(t-a)/(b-a)));
            Color c = mix(colors[segment],colors[segment+1],local);
            out.append(hex(c));
            if (decorations != null) for (String decoration : decorations) out.append(decoration);
            out.appendCodePoint(cp);
        }
        return out.toString();
    }

    public static String multiRgbGradient(String text, Color[] colors, double phase, Interpolator interpolator, List<String> decorations) {
        if (colors == null || colors.length < 2) return text == null ? "" : text;
        double[] positions = new double[colors.length];
        for (int i=0;i<positions.length;i++) positions[i] = i/(double)(positions.length-1);
        double normalized = phase - Math.floor(phase);
        if (normalized == 0d) return multiRgbGradient(text,colors,positions,interpolator,decorations);
        Color[] shifted = new Color[colors.length];
        for (int i=0;i<colors.length;i++) shifted[i] = colors[(i + (int)Math.floor(normalized*colors.length)) % colors.length];
        return multiRgbGradient(text,shifted,positions,interpolator,decorations);
    }

    private static Color mix(Color a, Color b, double t) {
        int r=(int)Math.round(a.getRed()+(b.getRed()-a.getRed())*t);
        int g=(int)Math.round(a.getGreen()+(b.getGreen()-a.getGreen())*t);
        int bl=(int)Math.round(a.getBlue()+(b.getBlue()-a.getBlue())*t);
        int al=(int)Math.round(a.getAlpha()+(b.getAlpha()-a.getAlpha())*t);
        return new Color(clamp(r),clamp(g),clamp(bl),clamp(al));
    }
    private static int clamp(int x){return Math.max(0,Math.min(255,x));}
    public static String hex(Color c){String h=String.format("%06x",c.getRGB()&0xffffff);StringBuilder b=new StringBuilder("§x");for(char ch:h.toCharArray())b.append('§').append(ch);return b.toString();}
}
