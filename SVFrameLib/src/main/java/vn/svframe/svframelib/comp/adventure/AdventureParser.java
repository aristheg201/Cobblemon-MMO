package vn.svframe.svframelib.comp.adventure;

import vn.svframe.svframelib.comp.adventure.argument.AdventureArgument;
import vn.svframe.svframelib.comp.adventure.argument.AdventureArgumentQueue;
import vn.svframe.svframelib.comp.adventure.resolver.ContextTagResolver;
import vn.svframe.svframelib.comp.adventure.tag.AdventureTag;
import vn.svframe.svframelib.comp.adventure.tag.implementation.*;
import vn.svframe.svframelib.comp.adventure.tag.implementation.decorations.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fabric-native port of MythicLib 1.7.1 legacy Adventure tag parser. */
public class AdventureParser {
    private static final Pattern TOKEN = Pattern.compile("<([^<>]+)>");
    private static final Pattern LEGACY = Pattern.compile("(?i)[§&][0-9A-FK-OR]");
    private static final Pattern LEGACY_HEX = Pattern.compile("(?i)§x(?:§[0-9A-F]){6}");
    private static final List<Character> DECORATIONS = List.of('k','l','m','n','o');
    private final List<AdventureTag> tags = new CopyOnWriteArrayList<>();

    /** Matches 1.7.1: the boolean constructor creates an empty parser. */
    public AdventureParser(boolean ignored) { }

    /** Matches 1.7.1: the no-arg parser installs all built-in tags. */
    public AdventureParser() {
        add(new GradientTag());
        add(new RainbowTag());
        add(new TransitionTag());
        add(new VanillaColorTag());
        add(new HexColorTag());
        add(new AdventureColorTag());
        add(new NewlineTag());
        add(new BoldTag());
        add(new ItalicTag());
        add(new ObfuscatedTag());
        add(new ResetTag());
        add(new StrikethroughTag());
        add(new UnderlineTag());
    }

    public String parse(String input) {
        if (input == null) return null;
        String out = input;
        int guard = 0;
        while (guard++ < 100) {
            Matcher matcher = TOKEN.matcher(out);
            boolean changed = false;
            while (matcher.find()) {
                String raw = matcher.group(1);
                if (raw.startsWith("/")) continue;
                ParsedTag parsed = parseToken(raw);
                Optional<AdventureTag> found = findByName(parsed.name());
                if (found.isEmpty()) continue;
                AdventureTag tag = found.get();
                AdventureArgumentQueue arguments = parseArguments(parsed.arguments());
                String replacement;
                int replaceEnd = matcher.end();
                if (tag.resolver() instanceof ContextTagResolver contextResolver) {
                    Closing closing = findClosing(out, matcher.end(), parsed.name(), tag);
                    String context = closing == null ? out.substring(matcher.end()) : out.substring(matcher.end(), closing.start());
                    List<String> decorations = activeDecorations(out.substring(0, matcher.start()));
                    replacement = contextResolver.resolve(parsed.name(), arguments, stripFormattingForGradient(parse(context)), decorations);
                    if (replacement == null) replacement = "";
                    replaceEnd = closing == null ? out.length() : closing.end();
                } else {
                    replacement = tag.resolver().resolve(parsed.name(), arguments);
                    if (replacement == null) replacement = "";
                }
                out = out.substring(0, matcher.start()) + replacement + out.substring(replaceEnd);
                changed = true;
                break;
            }
            if (!changed) break;
        }
        out = replaceClosingTags(out);
        return minecraftColorization(out);
    }

    public CompletableFuture<String> parseAsync(String input) { return CompletableFuture.supplyAsync(() -> parse(input)); }

    public Collection<String> parse(Collection<String> input) {
        if (input == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String line : input) {
            String parsed = parse(line);
            if (parsed != null && parsed.contains("\n")) out.addAll(Arrays.asList(parsed.split("\\n", -1)));
            else out.add(parsed);
        }
        return out;
    }

    public CompletableFuture<Collection<String>> parseAsync(Collection<String> input) { return CompletableFuture.supplyAsync(() -> parse(input)); }

    public String stripColors(String input) {
        if (input == null) return null;
        String out = TOKEN.matcher(input).replaceAll("");
        out = LEGACY_HEX.matcher(out).replaceAll("");
        return LEGACY.matcher(out).replaceAll("");
    }

    public String lastColor(String input, boolean includeDecorations) {
        if (input == null || input.isEmpty()) return "";
        String parsed = parse(input);
        String color = "";
        StringBuilder decorations = new StringBuilder();
        for (int i=0;i<parsed.length()-1;i++) {
            if (parsed.charAt(i) != '§') continue;
            char code = Character.toLowerCase(parsed.charAt(i+1));
            if (code == 'x' && i+13 < parsed.length()) {
                String candidate = parsed.substring(i, i+14);
                if (LEGACY_HEX.matcher(candidate).matches()) { color = candidate; decorations.setLength(0); i += 12; }
            } else if ("0123456789abcdef".indexOf(code) >= 0) {
                color = "§" + code;
                decorations.setLength(0);
            } else if (code == 'r') {
                color = "§r";
                decorations.setLength(0);
            } else if (includeDecorations && DECORATIONS.contains(code) && decorations.indexOf("§"+code) < 0) decorations.append('§').append(code);
        }
        return color + (includeDecorations ? decorations : "");
    }

    public void add(AdventureTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (findByName(tag.name()).isPresent()) throw new IllegalArgumentException("Adventure tag already registered: " + tag.name());
        tags.add(tag);
    }
    public void forceRegister(AdventureTag tag) { Objects.requireNonNull(tag,"tag"); tags.removeIf(existing -> sameIdentity(existing, tag)); tags.add(tag); }
    public void remove(AdventureTag tag) { tags.remove(tag); }
    public Optional<AdventureTag> findByName(String name) {
        if (name == null) return Optional.empty();
        String normalized = normalizeName(name);
        if (normalized.startsWith("#") && normalized.length() == 7) normalized = "#";
        String finalName = normalized;
        return tags.stream().filter(tag -> normalizeName(tag.name()).equals(finalName) || tag.aliases().stream().map(AdventureParser::normalizeName).anyMatch(finalName::equals)).findFirst();
    }
    public List<AdventureTag> tags() { return List.copyOf(tags); }

    private static ParsedTag parseToken(String raw) {
        String token = raw.trim();
        if (token.startsWith("#") && token.length() == 7) return new ParsedTag("#", token.substring(1));
        int split = token.indexOf(':');
        return split < 0 ? new ParsedTag(token, "") : new ParsedTag(token.substring(0,split), token.substring(split+1));
    }

    private static AdventureArgumentQueue parseArguments(String raw) {
        if (raw == null || raw.isBlank()) return new AdventureArgumentQueue(List.of());
        List<AdventureArgument> args = new ArrayList<>();
        for (String value : raw.split(":")) args.add(new AdventureArgument(value));
        return new AdventureArgumentQueue(args);
    }

    private Closing findClosing(String text, int from, String rawName, AdventureTag tag) {
        Matcher matcher = TOKEN.matcher(text);
        matcher.region(from, text.length());
        int depth = 0;
        while (matcher.find()) {
            String token = matcher.group(1).trim();
            boolean closing = token.startsWith("/");
            String candidate = closing ? token.substring(1) : parseToken(token).name();
            if (!matchesTag(candidate, tag)) continue;
            if (!closing) depth++;
            else if (depth-- == 0) return new Closing(matcher.start(), matcher.end());
        }
        return null;
    }

    private static boolean matchesTag(String name, AdventureTag tag) {
        String n = normalizeName(name);
        if (normalizeName(tag.name()).equals(n)) return true;
        for (String alias : tag.aliases()) if (normalizeName(alias).equals(n)) return true;
        return false;
    }

    private static String replaceClosingTags(String input) {
        Matcher matcher = TOKEN.matcher(input); StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String raw = matcher.group(1).trim();
            if (raw.startsWith("/")) matcher.appendReplacement(out, Matcher.quoteReplacement("§r"));
            else matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(out); return out.toString();
    }

    private static String minecraftColorization(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i=0;i<input.length();i++) {
            char c=input.charAt(i);
            if (c=='&' && i+1<input.length()) {
                char n=Character.toLowerCase(input.charAt(i+1));
                if ("0123456789abcdefklmnor".indexOf(n)>=0) { out.append('§').append(n); i++; continue; }
                if (n=='#' && i+7<input.length()) {
                    String hex=input.substring(i+2,i+8);
                    if (hex.matches("(?i)[0-9a-f]{6}")) { out.append(hexCode(hex)); i+=7; continue; }
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String hexCode(String hex) { StringBuilder out=new StringBuilder("§x"); for(char c:hex.toLowerCase(Locale.ROOT).toCharArray()) out.append('§').append(c); return out.toString(); }
    private static List<String> activeDecorations(String prefix) { String parsed=minecraftColorization(prefix); LinkedHashSet<String> active=new LinkedHashSet<>(); for(int i=0;i<parsed.length()-1;i++) if(parsed.charAt(i)=='§'){char c=Character.toLowerCase(parsed.charAt(i+1)); if("0123456789abcdefr".indexOf(c)>=0) active.clear(); else if(DECORATIONS.contains(c)) active.add("§"+c);} return List.copyOf(active); }
    private static String stripFormattingForGradient(String input) { return input == null ? "" : LEGACY.matcher(LEGACY_HEX.matcher(input).replaceAll("")).replaceAll(""); }
    private static String normalizeName(String name) { return name == null ? "" : name.trim().toLowerCase(Locale.ROOT); }
    private static boolean sameIdentity(AdventureTag a, AdventureTag b) { if (normalizeName(a.name()).equals(normalizeName(b.name()))) return true; for(String alias:a.aliases()) if(normalizeName(alias).equals(normalizeName(b.name()))) return true; return false; }
    private record ParsedTag(String name,String arguments) {}
    private record Closing(int start,int end) {}
}
