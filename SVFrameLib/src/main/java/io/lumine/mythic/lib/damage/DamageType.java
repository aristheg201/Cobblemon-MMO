package io.lumine.mythic.lib.damage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public enum DamageType {
    MAGIC,PHYSICAL,WEAPON,SKILL,PROJECTILE,UNARMED,ON_HIT,MINION,DOT;
    public String getPath(){return name().toLowerCase(Locale.ROOT);}public String getOffenseStat(){return name()+"_DAMAGE";}
    public static List<DamageType> listFromConfig(List<DamageType> fallback,Object raw){return raw==null?Objects.requireNonNull(fallback):listFromConfig(raw);}public static List<DamageType> listFromConfig(Object raw){if(raw instanceof String s){List<DamageType>out=new ArrayList<>();for(String x:s.split(","))out.add(parse(x));return out;}if(raw instanceof Iterable<?> it){List<DamageType>out=new ArrayList<>();for(Object x:it)out.add(parse(String.valueOf(x)));return out;}throw new IllegalArgumentException("Cannot parse DamageType list from "+raw);}private static DamageType parse(String s){return valueOf(s.trim().toUpperCase(Locale.ROOT).replace('-','_').replace(' ','_'));}
}
