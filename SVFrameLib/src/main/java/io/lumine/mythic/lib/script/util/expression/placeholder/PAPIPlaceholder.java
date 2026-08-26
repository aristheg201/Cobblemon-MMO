package io.lumine.mythic.lib.script.util.expression.placeholder;

import io.lumine.mythic.lib.script.util.expression.EvaluationException;
import io.lumine.mythic.lib.skill.SkillMetadata;
import vn.svframe.mythiclibfabric.runtime.NativePlaceholderRegistry;

/**
 * Fabric replacement for the original PlaceholderAPI-backed expression
 * placeholder. Percent placeholders are resolved through the native provider
 * registry so Fabric integrations can register providers without Bukkit/PAPI.
 */
public class PAPIPlaceholder implements ExpressionPlaceholder {
    private final String placeholderName;

    public PAPIPlaceholder(String placeholderName) {
        this.placeholderName = placeholderName;
    }

    @Override
    public Double parse(SkillMetadata metadata) {
        String result = NativePlaceholderRegistry.resolve(
                metadata.getCaster().getData().getUniqueId(), placeholderName);
        try {
            return Double.parseDouble(result);
        } catch (NumberFormatException exception) {
            throw new EvaluationException("Placeholder '%" + placeholderName + "%' did not resolve to a number: " + result, exception);
        }
    }
}
