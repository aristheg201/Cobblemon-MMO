package vn.svframe.svframelib.comp.adventure.resolver.implementation;

import vn.svframe.svframelib.comp.adventure.argument.AdventureArgumentQueue;
import vn.svframe.svframelib.comp.adventure.gradient.GradientBuilder;
import vn.svframe.svframelib.comp.adventure.gradient.Interpolator;
import vn.svframe.svframelib.comp.adventure.resolver.ContextTagResolver;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class GradientResolver implements ContextTagResolver {
    @Override public String resolve(String src, AdventureArgumentQueue q, String context, List<String> decorations) {
        List<Color> colors=new ArrayList<>(); double phase=0d;
        while(q.hasNext()) {String s=q.pop().value(); try{String h=s.startsWith("#")?s.substring(1):s; colors.add(new Color(Integer.parseInt(h,16)&0xffffff));}catch(RuntimeException e){try{phase=Double.parseDouble(s);}catch(NumberFormatException ignored){}}}
        if(colors.size()<2){colors.clear();colors.add(Color.WHITE);colors.add(Color.BLACK);}
        return GradientBuilder.multiRgbGradient(context,colors.toArray(Color[]::new),phase,Interpolator.LINEAR,decorations);
    }
}
