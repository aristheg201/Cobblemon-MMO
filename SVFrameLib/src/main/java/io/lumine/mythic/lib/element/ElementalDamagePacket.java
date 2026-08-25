package io.lumine.mythic.lib.element;
import io.lumine.mythic.lib.damage.*;
public final class ElementalDamagePacket extends DamagePacket {public ElementalDamagePacket(double value,Element element,DamageType...types){super(value,element,types);}@Override public Element getElement(){return super.getElement();}@Override public void setElement(Element element){super.setElement(element);}}
