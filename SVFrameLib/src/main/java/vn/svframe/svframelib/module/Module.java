package vn.svframe.svframelib.module;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Native Fabric equivalent of MythicLib 1.7.1 module lifecycle semantics. */
public abstract class Module {
    private final MMOPlugin plugin;
    private final String key;
    private volatile boolean enabled;
    private volatile boolean startup;
    private final List<ListenerToggle> listenerToggles = new ArrayList<>();

    protected Module() { this(null, null); }
    protected Module(MMOPlugin plugin) { this(plugin, null); }
    protected Module(MMOPlugin plugin, String key) {
        this.plugin = plugin;
        this.key = key == null ? getClass().getSimpleName() : key;
    }

    public boolean isEnabled() { return enabled; }
    protected boolean shouldEnable() { return true; }

    /** Retained for native callers; lifecycle state transitions still go through reload(). */
    public synchronized void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        if (enabled) enable(); else disable();
    }

    public synchronized void reload() {
        if (!startup) startup();
        if (shouldEnable()) {
            if (enabled) onReset();
            else enable();
            onReload();
        } else if (enabled) {
            onReset();
            disable();
        }
    }

    private void startup() {
        if (startup) throw new IllegalStateException("Module has already started up");
        startup = true;
        resolveModuleListeners();
        onStartup();
    }

    private void resolveModuleListeners() {
        listenerToggles.clear();
        for (Field field : getClass().getDeclaredFields()) {
            if (field.getAnnotation(ModuleListener.class) == null) continue;
            boolean accessible = field.canAccess(this);
            try {
                field.setAccessible(true);
                Object value = field.get(this);
                if (value instanceof ListenerToggle toggle) listenerToggles.add(toggle);
                else if (value != null)
                    throw new IllegalStateException("Unsupported native @ModuleListener field type " + field.getType().getName());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not resolve module listeners", exception);
            } finally {
                field.setAccessible(accessible);
            }
        }
    }

    private void enable() {
        if (enabled) throw new IllegalStateException("Module is already enabled");
        enabled = true;
        listenerToggles.forEach(toggle -> toggle.toggle(true));
        onEnable();
    }

    private void disable() {
        if (!enabled) throw new IllegalStateException("Module is already disabled");
        enabled = false;
        listenerToggles.forEach(ListenerToggle::disable);
        onDisable();
    }

    protected void onStartup() { }
    protected void onReset() { }
    protected void onEnable() { }
    protected void onDisable() { }
    protected void onReload() { }

    public String getModuleKey() { return key; }
    public MMOPlugin getPlugin() { return plugin; }
}
