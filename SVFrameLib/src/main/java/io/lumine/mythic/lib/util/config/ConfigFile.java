package io.lumine.mythic.lib.util.config;

import io.lumine.mythic.lib.module.MMOPlugin;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;
import java.nio.file.*;

public abstract class ConfigFile<T> {
    private final MMOPlugin plugin;
    private final Path file;
    private T content;
    protected ConfigFile(MMOPlugin plugin, String path, String name) {
        this.plugin = plugin;
        Path base = MythicLibFabricMod.configRoot();
        if (plugin != null && !"svframelib".equals(plugin.getNamespacedKey()))
            base = base.getParent().resolve(plugin.getNamespacedKey());
        this.file = base.resolve(path == null ? "" : path).resolve(name == null ? "" : name).normalize();
    }
    public T getContent(){ return content; }
    public boolean hasContent(){ return content != null; }
    public void setContent(T content){ this.content = content; }
    public boolean exists(){ return Files.exists(file); }
    public MMOPlugin getPlugin(){ return plugin; }
    public java.io.File getFile(){ return file.toFile(); }
    public Path getPath(){ return file; }
    public void delete(){ try { Files.deleteIfExists(file); } catch (java.io.IOException e) { throw new RuntimeException(e); } }
    public abstract void save();
}
