package io.lumine.mythic.lib.comp.flags;
public enum CustomFlag { MI_WEAPONS(true),MI_COMMANDS(true),MI_CONSUMABLES(true),MI_TOOLS(true),MMO_ABILITIES(true),PVP_MODE(false),@Deprecated ABILITY_PVP(true); private final boolean def; CustomFlag(boolean d){def=d;} public boolean getDefault(){return def;} public String getPath(){return name().toLowerCase().replace('_','-');} }
