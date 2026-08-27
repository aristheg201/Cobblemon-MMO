package vn.svframe.svframelib.fabric.runtime.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dependency-free expression runtime used by the native script and 1.7.1
 * compatibility expression APIs.
 */
public final class ExpressionRuntime {
    public double evaluate(String expression, Map<String, Double> variables) {
        return new Parser(substitute(expression, variables)).parseNumber();
    }

    public boolean evaluateBoolean(String expression, Map<String, Double> variables) {
        return new Parser(substitute(expression, variables)).parseBooleanExpression();
    }

    private static String substitute(String expression, Map<String, Double> variables) {
        String value = expression == null ? "0" : expression.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        if (variables != null) {
            for (Map.Entry<String, Double> entry : variables.entrySet()) {
                value = value.replace("<" + entry.getKey() + ">", Double.toString(entry.getValue()));
            }
        }
        value = value.replaceAll("(?i)<random\\.double>", Double.toString(Math.random()));
        return value;
    }

    private static final class Parser {
        private static final double BOOLEAN_EPSILON = 1.0E-10d;
        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input;
        }

        private double parseNumber() {
            double value = add();
            skipWhitespace();
            if (index != input.length()) {
                throw new IllegalArgumentException("Bad expression at " + index + " in " + input);
            }
            return value;
        }

        private boolean parseBooleanExpression() {
            boolean value = or();
            skipWhitespace();
            if (index != input.length()) {
                throw new IllegalArgumentException("Bad boolean expression at " + index + " in " + input);
            }
            return value;
        }

        private boolean or() {
            boolean value = and();
            while (true) {
                if (eat("||")) value = value | and();
                else return value;
            }
        }

        private boolean and() {
            boolean value = booleanUnary();
            while (true) {
                if (eat("&&")) value = value & booleanUnary();
                else return value;
            }
        }

        private boolean booleanUnary() {
            skipWhitespace();
            if (eat("!")) return !booleanUnary();
            if (peekWord("true")) {
                word();
                return true;
            }
            if (peekWord("false")) {
                word();
                return false;
            }
            if (eat("(")) {
                int checkpoint = index;
                try {
                    boolean nested = or();
                    if (!eat(")")) throw new IllegalArgumentException("Missing )");
                    return nested;
                } catch (IllegalArgumentException ignored) {
                    index = checkpoint - 1;
                }
            }

            double left = add();
            if (eat("==")) return Math.abs(left - add()) <= BOOLEAN_EPSILON;
            if (eat("!=")) return Math.abs(left - add()) > BOOLEAN_EPSILON;
            if (eat(">=")) return left >= add();
            if (eat("<=")) return left <= add();
            if (eat(">")) return left > add();
            if (eat("<")) return left < add();
            return Math.abs(left) > BOOLEAN_EPSILON;
        }

        private double add() {
            double value = multiply();
            while (true) {
                if (eat("+")) value += multiply();
                else if (eat("-")) value -= multiply();
                else return value;
            }
        }

        private double multiply() {
            double value = power();
            while (true) {
                if (eat("*")) value *= power();
                else if (eat("/")) value /= power();
                else if (eat("%")) value %= power();
                else return value;
            }
        }

        private double power() {
            double value = unary();
            if (eat("^")) value = Math.pow(value, power());
            return value;
        }

        private double unary() {
            skipWhitespace();
            if (eat("+")) return unary();
            if (eat("-")) return -unary();
            if (eat("(")) {
                double value = add();
                if (!eat(")")) throw new IllegalArgumentException("Missing )");
                return value;
            }

            if (index < input.length() && (Character.isLetter(input.charAt(index)) || input.charAt(index) == '_')) {
                String symbol = word().toLowerCase(Locale.ROOT);
                if (symbol.equals("pi")) return Math.PI;
                if (symbol.equals("e")) return Math.E;
                if (!eat("(")) throw new IllegalArgumentException("Unknown symbol " + symbol);

                List<Double> args = new ArrayList<>();
                if (!peek(')')) {
                    do {
                        args.add(add());
                    } while (eat(","));
                }
                if (!eat(")")) throw new IllegalArgumentException("Missing ) after " + symbol);
                return function(symbol, args);
            }

            int start = index;
            while (index < input.length()) {
                char current = input.charAt(index);
                if (Character.isDigit(current) || current == '.') {
                    index++;
                    continue;
                }
                if (current == 'e' || current == 'E') {
                    index++;
                    if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) index++;
                    continue;
                }
                break;
            }
            if (start == index) throw new IllegalArgumentException("Number expected at " + index + " in " + input);
            return Double.parseDouble(input.substring(start, index));
        }

        private double function(String function, List<Double> args) {
            return switch (function) {
                case "random" -> {
                    arity(function, args, 0);
                    yield Math.random();
                }
                case "atan2" -> {
                    arity(function, args, 2);
                    yield Math.atan2(args.get(0), args.get(1));
                }
                case "pow" -> {
                    arity(function, args, 2);
                    yield Math.pow(args.get(0), args.get(1));
                }
                case "min" -> {
                    arity(function, args, 2);
                    yield Math.min(args.get(0), args.get(1));
                }
                case "max" -> {
                    arity(function, args, 2);
                    yield Math.max(args.get(0), args.get(1));
                }
                case "clamp" -> {
                    arity(function, args, 3);
                    // SVFrameLib 1.7.1 signature is clamp(min, value, max).
                    yield Math.min(args.get(2), Math.max(args.get(0), args.get(1)));
                }
                case "non_zero" -> {
                    arity(function, args, 2);
                    yield args.get(0) == 0d ? args.get(1) : args.get(0);
                }
                case "sin" -> Math.sin(one(function, args));
                case "cos" -> Math.cos(one(function, args));
                case "tan" -> Math.tan(one(function, args));
                case "sqrt" -> Math.sqrt(one(function, args));
                case "abs" -> Math.abs(one(function, args));
                case "floor" -> Math.floor(one(function, args));
                case "ceil" -> Math.ceil(one(function, args));
                case "round" -> Math.rint(one(function, args));
                default -> throw new IllegalArgumentException("Unknown function " + function);
            };
        }

        private static double one(String function, List<Double> args) {
            arity(function, args, 1);
            return args.get(0);
        }

        private static void arity(String function, List<Double> args, int expected) {
            if (args.size() != expected) {
                throw new IllegalArgumentException(function + " expects " + expected + " argument" + (expected == 1 ? "" : "s"));
            }
        }

        private boolean peekWord(String expected) {
            skipWhitespace();
            if (!input.regionMatches(true, index, expected, 0, expected.length())) return false;
            int end = index + expected.length();
            return end == input.length() || !Character.isLetterOrDigit(input.charAt(end));
        }

        private String word() {
            skipWhitespace();
            int start = index;
            while (index < input.length()) {
                char current = input.charAt(index);
                if (Character.isLetterOrDigit(current) || current == '_' || current == '.') index++;
                else break;
            }
            return input.substring(start, index);
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++;
        }

        private boolean eat(String token) {
            skipWhitespace();
            if (!input.startsWith(token, index)) return false;
            index += token.length();
            return true;
        }

        private boolean peek(char token) {
            skipWhitespace();
            return index < input.length() && input.charAt(index) == token;
        }
    }
}
