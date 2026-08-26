package vn.svframe.svframelib.comp.adventure.resolver;

import vn.svframe.svframelib.comp.adventure.argument.AdventureArgumentQueue;
import java.util.List;

@FunctionalInterface
public interface ContextTagResolver extends AdventureTagResolver {
    String resolve(String src, AdventureArgumentQueue argumentQueue, String context, List<String> decorations);
    @Override
    default String resolve(String src, AdventureArgumentQueue argumentQueue) { return null; }
}
