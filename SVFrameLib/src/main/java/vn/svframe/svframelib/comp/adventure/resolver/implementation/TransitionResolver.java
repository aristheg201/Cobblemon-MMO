package vn.svframe.svframelib.comp.adventure.resolver.implementation;

import vn.svframe.svframelib.comp.adventure.argument.AdventureArgumentQueue;
import vn.svframe.svframelib.comp.adventure.gradient.GradientBuilder;
import vn.svframe.svframelib.comp.adventure.resolver.ContextTagResolver;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class TransitionResolver implements ContextTagResolver {
    @Override public String resolve(String src, AdventureArgumentQueue q, String context, List<String> decorations){List<String> raw=new ArrayList<>();while(q.hasNext())raw.add(q.pop().value());if(raw.size()<3)return null;double phase=1d;try{phase=Double.parseDouble(raw.get(raw.size()-1));raw.remove(raw.size()-1);}catch(NumberFormatException ignored){}if(phase<0||phase>1||raw.size()<2)return null;List<Color> colors=new ArrayList<>();for(String s:raw){try{String h=s.startsWith("#")?s.substring(1):s;colors.add(new Color(Integer.parseInt(h,16)&0xffffff));}catch(RuntimeException ignored){return null;}}double scaled=phase*(colors.size()-1);int i=Math.min(colors.size()-2,(int)Math.floor(scaled));double local=scaled-i;Color a=colors.get(i),b=colors.get(i+1);Color c=new Color((int)(a.getRed()+(b.getRed()-a.getRed())*local),(int)(a.getGreen()+(b.getGreen()-a.getGreen())*local),(int)(a.getBlue()+(b.getBlue()-a.getBlue())*local));StringBuilder out=new StringBuilder(GradientBuilder.hex(c));if(decorations!=null)decorations.forEach(out::append);out.append(context);return out.toString();}
}
