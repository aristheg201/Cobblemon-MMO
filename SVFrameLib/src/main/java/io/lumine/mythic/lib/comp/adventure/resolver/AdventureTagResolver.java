package io.lumine.mythic.lib.comp.adventure.resolver;
import io.lumine.mythic.lib.comp.adventure.argument.AdventureArgumentQueue;
@FunctionalInterface public interface AdventureTagResolver { String resolve(String content, AdventureArgumentQueue arguments); }
