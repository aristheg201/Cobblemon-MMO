package vn.svframe.svframelib.comp.adventure.resolver.implementation;

import vn.svframe.svframelib.comp.adventure.argument.AdventureArgumentQueue;
import vn.svframe.svframelib.comp.adventure.resolver.AdventureTagResolver;
import vn.svframe.svframelib.util.AdventureUtils;

public class VanillaColorResolver implements AdventureTagResolver {
    @Override public String resolve(String src, AdventureArgumentQueue arguments) {
        return AdventureUtils.getByName(src).map(Object::toString).orElse(null);
    }
}
