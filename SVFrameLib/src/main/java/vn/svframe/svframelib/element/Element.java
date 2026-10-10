package vn.svframe.svframelib.element;

import vn.svframe.svframelib.fabric.SVFrameLibCombatRuntime;
import vn.svframe.svframelib.fabric.runtime.NativeElementRegistry;
import java.util.*;

public final class Element {
    private final String id,name,icon,loreIcon,color,regular,critical;

    public Element(String id,String name,String icon,String loreIcon,String color,String regular,String critical){
        this(id,name,icon,loreIcon,color,regular,critical,true);
    }

    private Element(String id,String name,String icon,String loreIcon,String color,String regular,String critical,boolean requireAttackSkill){
        this.id=norm(id);
        this.name=Objects.requireNonNull(name);
        this.icon=icon==null?"DIRT":icon;
        this.loreIcon=loreIcon==null?"?":loreIcon;
        this.color=color==null?"&f":color;
        this.regular=requireAttackSkill?Objects.requireNonNull(regular):regular;
        this.critical=critical;
    }

    private Element(NativeElementRegistry.Element e){
        this(e.id(),e.name(),e.icon(),e.loreIcon(),e.color(),e.regularAttack(),e.criticalStrike());
    }

    public String getId(){return id;}
    public String getName(){return name;}
    public String getIcon(){return icon;}
    public String getLoreIcon(){return loreIcon;}
    public String getColor(){return color;}
    public boolean hasAttackSkill(){return regular!=null;}
    public String getSkill(boolean criticalStrike){
        if(regular==null)throw new IllegalStateException("Element "+id+" is a damage-only element and has no attack skill");
        return criticalStrike&&critical!=null?critical:regular;
    }

    /**
     * Resolve a configured combat element when one exists, otherwise create an unregistered
     * damage-only identity. Damage-only elements deliberately do not invent a regular-attack
     * skill and are safe for integrations that only need packet-level elemental metadata.
     */
    public static Element forDamage(String id,String name){
        String normalized=norm(id);
        NativeElementRegistry.Element configured=SVFrameLibCombatRuntime.elements().get(normalized);
        return configured!=null?new Element(configured):new Element(normalized,Objects.requireNonNull(name),"DIRT","?","&f",null,null,false);
    }

    public static Collection<Element> values(){
        List<Element>out=new ArrayList<>();
        for(NativeElementRegistry.Element e:SVFrameLibCombatRuntime.elements().values())out.add(new Element(e));
        return List.copyOf(out);
    }

    public static Element valueOf(String id){
        NativeElementRegistry.Element e=SVFrameLibCombatRuntime.elements().get(id);
        if(e==null)throw new IllegalArgumentException("Unknown element: "+id);
        return new Element(e);
    }

    @Override public boolean equals(Object o){return o instanceof Element e&&id.equals(e.id);}
    @Override public int hashCode(){return id.hashCode();}
    @Override public String toString(){return id;}
    private static String norm(String s){return Objects.requireNonNull(s).trim().toUpperCase(Locale.ROOT).replace('-','_').replace(' ','_');}
}
