package vn.svframe.svframelib.script.util.expression.numeric;

import vn.svframe.svframelib.script.util.expression.EvaluationException;
import vn.svframe.svframelib.script.util.expression.placeholder.ExpressionPlaceholder;
import vn.svframe.svframelib.script.util.expression.placeholder.MythicLibVariablePlaceholder;
import vn.svframe.svframelib.script.util.expression.placeholder.PAPIPlaceholder;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.Lazy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native precompiled numeric expression retaining the 1.7.1 placeholder phases. */
public class PrecompiledNumericExpression extends NumericExpression {
    private static final Pattern PAPI_PLACEHOLDER_PATTERN = Pattern.compile("%([^!&|<>=%]+)%");
    private static final Pattern CUSTOM_PLACEHOLDER_PATTERN = Pattern.compile("\\{([^{}]*)}");

    private final String originalExpression;
    private final String compiledExpression;
    private final List<ExpressionPlaceholder> placeholders = new ArrayList<>();

    public PrecompiledNumericExpression(String expression,
                                        Function<String, ExpressionPlaceholder> customPlaceholderResolver) {
        if (expression == null) throw new IllegalArgumentException("Expression cannot be null");
        this.originalExpression = expression;

        String compiled = resolvePlaceholders(expression, SkillMetadata.INTERNAL_PLACEHOLDER_PATTERN,
                MythicLibVariablePlaceholder::new);
        compiled = resolvePlaceholders(compiled, PAPI_PLACEHOLDER_PATTERN, PAPIPlaceholder::new);
        if (customPlaceholderResolver != null) {
            compiled = resolvePlaceholders(compiled, CUSTOM_PLACEHOLDER_PATTERN, customPlaceholderResolver);
        }
        this.compiledExpression = compiled;

        // Validate expression syntax once, just like Crunch.compileExpression did.
        Map<String, Double> zeroes = new LinkedHashMap<>();
        for (int i = 0; i < placeholders.size(); i++) zeroes.put(variableName(i), 0d);
        RUNTIME.evaluate(compiledExpression, zeroes);
    }

    private String resolvePlaceholders(String input,
                                       Pattern pattern,
                                       Function<String, ExpressionPlaceholder> resolver) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer output = new StringBuffer(input.length());
        while (matcher.find()) {
            String key = matcher.group(1);
            ExpressionPlaceholder placeholder = resolver.apply(key);
            if (placeholder == null) throw new IllegalArgumentException("No placeholder resolver for '" + key + "'");
            int index = placeholders.size();
            placeholders.add(placeholder);
            matcher.appendReplacement(output, Matcher.quoteReplacement("<" + variableName(index) + ">"));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String variableName(int index) {
        return "expr_" + (index + 1);
    }

    @Override
    public synchronized double evaluate(SkillMetadata metadata) {
        return evaluate(Lazy.of(metadata));
    }

    @Override
    public synchronized double evaluate(Lazy<SkillMetadata> lazyMetadata) {
        try {
            SkillMetadata metadata = lazyMetadata.get();
            Map<String, Double> values = new LinkedHashMap<>();
            for (int i = 0; i < placeholders.size(); i++) {
                Double value = placeholders.get(i).parse(metadata);
                if (value == null || !Double.isFinite(value)) {
                    throw new EvaluationException("Placeholder " + (i + 1) + " produced invalid numeric value " + value);
                }
                values.put(variableName(i), value);
            }
            return RUNTIME.evaluate(compiledExpression, values);
        } catch (EvaluationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EvaluationException("Error evaluating expression '" + originalExpression + "'", exception);
        }
    }
}
