package vn.svframe.svframelib.comp.adventure.resolver.implementation;

import vn.svframe.svframelib.comp.adventure.argument.AdventureArgumentQueue;
import vn.svframe.svframelib.comp.adventure.gradient.GradientBuilder;
import vn.svframe.svframelib.comp.adventure.gradient.Interpolator;
import vn.svframe.svframelib.comp.adventure.resolver.ContextTagResolver;
import java.awt.Color;
import java.util.List;

public class RainbowResolver implements ContextTagResolver {
    private static final Color[] COLORS={new Color(255,0,0),new Color(255,165,0),new Color(255,255,0),new Color(0,180,0),new Color(62,136,252),new Color(148,0,211),new Color(255,0,0)};
    @Override public String resolve(String src, AdventureArgumentQueue q, String context, List<String> decorations){double phase=0d;if(q.hasNext())try{phase=Double.parseDouble(q.pop().value());}catch(NumberFormatException ignored){}return GradientBuilder.multiRgbGradient(context,COLORS,phase,Interpolator.LINEAR,decorations);}
}
