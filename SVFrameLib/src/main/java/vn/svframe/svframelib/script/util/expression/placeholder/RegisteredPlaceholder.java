package vn.svframe.svframelib.script.util.expression.placeholder;

import vn.svframe.svframelib.script.util.expression.EvaluationException;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.fabric.runtime.NativePlaceholderRegistry;

/**
 * Fabric replacement for the original provider-registry-backed expression
 * placeholder. Percent placeholders are resolved through the native provider
 * registry so Fabric integrations can register providers without server-plugin placeholder APIs.
 */
public class RegisteredPlaceholder implements ExpressionPlaceholder {
    private final String placeholderName;

    public RegisteredPlaceholder(String placeholderName) {
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
