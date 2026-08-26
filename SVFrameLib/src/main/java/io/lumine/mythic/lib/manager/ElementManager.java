package io.lumine.mythic.lib.manager;

import io.lumine.mythic.lib.element.Element;
import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.module.Module;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Native element registry with the same public lookup surface as MythicLib 1.7.1. */
public class ElementManager extends Module {
    private final Map<String, Element> mapped = new LinkedHashMap<>();

    public ElementManager(MMOPlugin plugin) {
        super(plugin, "element");
        for (Element element : Element.values()) mapped.put(norm(element.getId()), element);
    }

    public synchronized void register(Element element) {
        Objects.requireNonNull(element, "element");
        String id = norm(element.getId());
        if (mapped.putIfAbsent(id, element) != null) throw new IllegalArgumentException("Element already registered: " + id);
    }

    public synchronized void reset() {
        mapped.clear();
        for (Element element : Element.values()) mapped.put(norm(element.getId()), element);
    }

    public synchronized Element get(String id) {
        return Objects.requireNonNull(getOrNull(id), "Could not find element '" + id + "'");
    }

    public synchronized Element getOrNull(String id) {
        Element value = mapped.get(norm(id));
        if (value != null) return value;
        try {
            value = Element.valueOf(id);
            mapped.put(norm(value.getId()), value);
            return value;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public synchronized Collection<Element> getAll() {
        for (Element element : Element.values()) mapped.putIfAbsent(norm(element.getId()), element);
        return java.util.List.copyOf(mapped.values());
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
