package vn.svframe.svframelib.comp.adventure.tag.implementation;
import vn.svframe.svframelib.comp.adventure.resolver.implementation.HexColorResolver;import vn.svframe.svframelib.comp.adventure.tag.AdventureTag;
public class HexColorTag extends AdventureTag { public HexColorTag(){super("HEX",new HexColorResolver(),false,true,"#");} }
