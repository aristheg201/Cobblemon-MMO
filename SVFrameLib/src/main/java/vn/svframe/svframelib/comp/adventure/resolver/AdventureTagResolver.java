package vn.svframe.svframelib.comp.adventure.resolver;
import vn.svframe.svframelib.comp.adventure.argument.AdventureArgumentQueue;
@FunctionalInterface public interface AdventureTagResolver { String resolve(String content, AdventureArgumentQueue arguments); }
