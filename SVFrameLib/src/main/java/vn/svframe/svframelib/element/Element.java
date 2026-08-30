package vn.svframe.svframelib.element;

import vn.svframe.svframelib.fabric.SVFrameLibCombatRuntime;
import vn.svframe.svframelib.fabric.runtime.NativeElementRegistry;
import java.util.*;

public final class Element {
    private final String id,name,icon,loreIcon,color,regular,critical;
    public Element(String id,String name,String icon,String loreIcon,String color,String regular,String critical){this.id=norm(id);this.name=Objects.requireNonNull(name);this.icon=icon==null?"DIRT":icon;this.loreIcon=loreIcon==null?"?":loreIcon;this.color=color==null?"&f":color;this.regular=Objects.requireNonNull(regular);this.critical=critical;}
    private Element(NativeElementRegistry.Element e){this(e.id(),e.name(),e.icon(),e.loreIcon(),e.color(),e.regularAttack(),e.criticalStrike());}
    public String getId(){return id;}public String getName(){return name;}public String getIcon(){return icon;}public String getLoreIcon(){return loreIcon;}public String getColor(){return color;}public String getSkill(boolean criticalStrike){return criticalStrike&&critical!=null?critical:regular;}
    public static Collection<Element> values(){List<Element>out=new ArrayList<>();for(NativeElementRegistry.Element e:SVFrameLibCombatRuntime.elements().values())out.add(new Element(e));return List.copyOf(out);}public static Element valueOf(String id){NativeElementRegistry.Element e=SVFrameLibCombatRuntime.elements().get(id);if(e==null)throw new IllegalArgumentException("Unknown element: "+id);return new Element(e);}
    @Override public boolean equals(Object o){return o instanceof Element e&&id.equals(e.id);}@Override public int hashCode(){return id.hashCode();}@Override public String toString(){return id;}private static String norm(String s){return Objects.requireNonNull(s).trim().toUpperCase(Locale.ROOT).replace('-','_').replace(' ','_');}
}
