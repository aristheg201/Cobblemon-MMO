package vn.svframe.svframelib.player.modifier;

import vn.svframe.svframelib.util.Pair;

public enum ModifierType {
    RELATIVE, ADDITIVE_MULTIPLIER, FLAT;
    public String toStringSuffix(){return switch(this){case RELATIVE->"%";case ADDITIVE_MULTIPLIER->"s";case FLAT->"";};}
    public static Pair<ModifierType,Double> pairFromString(String value){if(value==null||value.isEmpty())throw new IllegalArgumentException("String cannot be empty");char c=value.charAt(value.length()-1);ModifierType type=switch(c){case '%','c','m'->RELATIVE;case 'a','s'->ADDITIVE_MULTIPLIER;default->FLAT;};String number=type==FLAT?value:value.substring(0,value.length()-1);return Pair.of(type,Double.parseDouble(number));}
}
