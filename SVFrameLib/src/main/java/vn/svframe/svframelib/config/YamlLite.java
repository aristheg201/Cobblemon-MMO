package vn.svframe.svframelib.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal YAML reader for SVFrameLib's bundled 1.7.1 configuration corpus. */
public final class YamlLite {
    private record Line(int indent, String text) { }

    private final List<Line> lines;
    private int index;

    private YamlLite(List<Line> lines) {
        this.lines = lines;
    }

    public static Object parse(String text) {
        text = text.replace("\uFEFF", "");
        String trimmed = text.trim();
        if (trimmed.equals("{}")) return new LinkedHashMap<String, Object>();
        if (trimmed.equals("[]")) return new ArrayList<Object>();

        List<Line> parsed = new ArrayList<>();
        for (String raw : text.replace("\r", "").split("\n")) {
            String clean = stripComment(raw);
            if (clean.isBlank()) continue;
            int indent = 0;
            while (indent < clean.length() && clean.charAt(indent) == ' ') indent++;
            if (indent < clean.length() && clean.charAt(indent) == '\t')
                throw new IllegalArgumentException("tabs not supported");
            parsed.add(new Line(indent, clean.substring(indent)));
        }
        return parsed.isEmpty() ? new LinkedHashMap<>() : new YamlLite(parsed).block(parsed.getFirst().indent());
    }

    public static Object parse(Path path) throws IOException {
        return parse(Files.readString(path));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object object) {
        return (Map<String, Object>) object;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object object) {
        return (List<Object>) object;
    }

    private Object block(int indent) {
        if (index >= lines.size()) return new LinkedHashMap<>();
        String text = lines.get(index).text();
        return text.startsWith("- ") || text.equals("-") ? listBlock(indent) : mapBlock(indent);
    }

    private Map<String, Object> mapBlock(int indent) {
        Map<String, Object> out = new LinkedHashMap<>();
        while (index < lines.size()) {
            Line line = lines.get(index);
            if (line.indent() < indent) break;
            if (line.indent() != indent || line.text().startsWith("- ")) break;

            int colon = findColon(line.text());
            if (colon < 0) throw new IllegalArgumentException("expected key: value at " + line.text());
            String key = unquote(line.text().substring(0, colon).trim());
            String rhs = line.text().substring(colon + 1).trim();
            index++;

            Object value;
            if (!rhs.isEmpty()) {
                String joined = rhs;
                if (index < lines.size() && lines.get(index).indent() > indent
                        && isScalarContinuationLine(rhs, lines.get(index).text())) {
                    StringBuilder builder = new StringBuilder(rhs);
                    while (index < lines.size() && lines.get(index).indent() > indent
                            && isScalarContinuationLine(builder.toString(), lines.get(index).text())) {
                        builder.append(' ').append(lines.get(index).text().trim());
                        index++;
                        if (isQuotedStart(builder.toString()) && quoteClosed(builder.toString())) break;
                    }
                    joined = builder.toString();
                }
                value = scalar(joined);
            } else if (index < lines.size() && lines.get(index).indent() > indent) {
                value = block(lines.get(index).indent());
            } else if (index < lines.size() && lines.get(index).indent() == indent
                    && lines.get(index).text().startsWith("-")) {
                value = listBlock(indent);
            } else {
                value = new LinkedHashMap<String, Object>();
            }
            out.put(key, value);
        }
        return out;
    }

    private List<Object> listBlock(int indent) {
        List<Object> out = new ArrayList<>();
        while (index < lines.size()) {
            Line line = lines.get(index);
            if (line.indent() < indent) break;
            if (line.indent() != indent || !line.text().startsWith("-")) break;

            String rest = line.text().length() == 1 ? "" : line.text().substring(1).trim();
            index++;
            if (rest.isEmpty()) {
                out.add(index < lines.size() && lines.get(index).indent() > indent
                        ? block(lines.get(index).indent()) : null);
                continue;
            }

            int colon = findMappingColon(rest);
            if (colon > 0 && !isQuoted(rest)) {
                Map<String, Object> map = new LinkedHashMap<>();
                String key = unquote(rest.substring(0, colon).trim());
                String rhs = rest.substring(colon + 1).trim();
                if (!rhs.isEmpty()) map.put(key, scalar(rhs));
                else if (index < lines.size() && lines.get(index).indent() > indent)
                    map.put(key, block(lines.get(index).indent()));
                else map.put(key, new LinkedHashMap<>());

                if (index < lines.size() && lines.get(index).indent() > indent) {
                    int child = lines.get(index).indent();
                    if (!lines.get(index).text().startsWith("- ")) map.putAll(mapBlock(child));
                }
                out.add(map);
            } else {
                if (index < lines.size() && lines.get(index).indent() > indent
                        && isScalarContinuationLine(rest, lines.get(index).text())) {
                    StringBuilder joined = new StringBuilder(rest);
                    while (index < lines.size() && lines.get(index).indent() > indent
                            && isScalarContinuationLine(joined.toString(), lines.get(index).text())) {
                        joined.append(' ').append(lines.get(index).text().trim());
                        index++;
                        if (isQuotedStart(joined.toString()) && quoteClosed(joined.toString())) break;
                    }
                    out.add(scalar(joined.toString()));
                } else {
                    out.add(scalar(rest));
                }
            }
        }
        return out;
    }

    private static boolean isQuotedStart(String value) {
        value = value.trim();
        return value.startsWith("'") || value.startsWith("\"");
    }

    private static boolean isScalarContinuationLine(String current, String next) {
        current = current.trim();
        next = next.trim();
        if (isQuotedStart(current) && !quoteClosed(current)) return true;
        if (next.isEmpty() || next.startsWith("-")) return false;
        return findMappingColon(next) < 0;
    }

    private static boolean quoteClosed(String value) {
        value = value.trim();
        if (value.startsWith("'")) return value.length() > 1 && value.endsWith("'");
        if (value.startsWith("\"")) return value.length() > 1 && value.endsWith("\"");
        return true;
    }

    private static Object scalar(String value) {
        value = value.trim();
        if (value.isEmpty()) return "";
        if ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\""))) return unquote(value);
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        if (value.equalsIgnoreCase("null") || value.equals("~")) return null;
        if (value.startsWith("[") && value.endsWith("]"))
            return inlineList(value.substring(1, value.length() - 1));
        if (value.startsWith("{") && value.endsWith("}"))
            return inlineMap(value.substring(1, value.length() - 1));
        try {
            if (value.matches("[-+]?\\d+")) return Long.parseLong(value);
            if (value.matches("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)(?:[eE][-+]?\\d+)?"))
                return Double.parseDouble(value);
        } catch (NumberFormatException ignored) { }
        return value;
    }

    private static List<Object> inlineList(String body) {
        List<Object> out = new ArrayList<>();
        for (String part : splitTop(body, ',')) if (!part.isBlank()) out.add(scalar(part));
        return out;
    }

    private static Map<String, Object> inlineMap(String body) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String part : splitTop(body, ',')) {
            int colon = findColon(part);
            if (colon < 0) continue;
            out.put(unquote(part.substring(0, colon).trim()), scalar(part.substring(colon + 1)));
        }
        return out;
    }

    private static List<String> splitTop(String input, char separator) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (quote != 0) {
                if (current == quote && (i == 0 || input.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (current == '\'' || current == '\"') {
                quote = current;
                continue;
            }
            if (current == '[' || current == '{') depth++;
            else if (current == ']' || current == '}') depth--;
            else if (current == separator && depth == 0) {
                out.add(input.substring(start, i).trim());
                start = i + 1;
            }
        }
        out.add(input.substring(start).trim());
        return out;
    }

    private static int findMappingColon(String input) {
        char quote = 0;
        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (quote != 0) {
                if (current == quote && (i == 0 || input.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (current == '\'' || current == '\"') quote = current;
            else if (current == '[' || current == '{') depth++;
            else if (current == ']' || current == '}') depth--;
            else if (current == ':' && depth == 0
                    && (i + 1 == input.length() || Character.isWhitespace(input.charAt(i + 1)))) return i;
        }
        return -1;
    }

    private static int findColon(String input) {
        char quote = 0;
        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (quote != 0) {
                if (current == quote && (i == 0 || input.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (current == '\'' || current == '\"') quote = current;
            else if (current == '[' || current == '{') depth++;
            else if (current == ']' || current == '}') depth--;
            else if (current == ':' && depth == 0) return i;
        }
        return -1;
    }

    private static boolean isQuoted(String value) {
        return (value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\""));
    }

    private static String unquote(String value) {
        if (isQuoted(value)) value = value.substring(1, value.length() - 1);
        return value.replace("\\\"", "\"").replace("\\'", "'");
    }

    private static String stripComment(String input) {
        char quote = 0;
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (quote != 0) {
                if (current == quote && (i == 0 || input.charAt(i - 1) != '\\')) quote = 0;
            } else if (current == '\'' || current == '\"') {
                quote = current;
            } else if (current == '#' && (i == 0 || Character.isWhitespace(input.charAt(i - 1)))) {
                return rtrim(input.substring(0, i));
            }
        }
        return rtrim(input);
    }

    private static String rtrim(String value) {
        int index = value.length();
        while (index > 0 && Character.isWhitespace(value.charAt(index - 1))) index--;
        return value.substring(0, index);
    }
}
