package vn.svframe.svframelib.comp.adventure.tag.implementation;
import vn.svframe.svframelib.comp.adventure.resolver.implementation.TransitionResolver;import vn.svframe.svframelib.comp.adventure.tag.AdventureTag;
public class TransitionTag extends AdventureTag { public TransitionTag(){super("transition",new TransitionResolver(),false,true);} }
