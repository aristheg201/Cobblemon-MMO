package vn.svframe.svframemobs;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Explicit owner-approved missing-source exception; never a fallback for unsupported mechanics. */
public final class SourceWaivers {
    private static final Pattern NINJA=Pattern.compile("(?i)^\\s*skill\\{[^}]*\\b(?:s|skill)\\s*=\\s*[\"']?NinjaDamageEvent[\"']?\\s*(?:;|}).*$");
    private SourceWaivers(){}
    public static Map<String,Object> apply(String owner,Map<String,Object> definition,Predicate<String> skillExists){
        if(!owner.equals("WardenAttackDummy")||skillExists.test("NinjaDamageEvent"))return definition;
        Object value=definition.get("Skills");if(!(value instanceof List<?>))return definition;
        List<?> original=(List<?>)value;
        List<?> retained=original.stream().filter(line->!NINJA.matcher(String.valueOf(line)).matches()).toList();
        if(retained.size()==original.size())return definition;
        var copy=new LinkedHashMap<>(definition);copy.put("Skills",retained);return Collections.unmodifiableMap(copy);
    }
}
