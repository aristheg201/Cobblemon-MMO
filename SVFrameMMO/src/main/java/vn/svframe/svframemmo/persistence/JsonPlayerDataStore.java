package vn.svframe.svframemmo.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Legacy native JSON store retained for lossless migration and explicit export. */
public final class JsonPlayerDataStore implements PlayerDataStore {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;

    public JsonPlayerDataStore(Path file) { this.file = file; }
    @Override public String id() { return "JSON"; }
    public Path file() { return file; }
    public boolean exists() { return Files.isRegularFile(file); }

    @Override
    public Map<UUID, PlayerDataSnapshot> loadAll() throws Exception {
        LinkedHashMap<UUID, PlayerDataSnapshot> result = new LinkedHashMap<>();
        if (!exists()) return result;
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                UUID id = UUID.fromString(entry.getKey());
                result.put(id, gson.fromJson(entry.getValue(), PlayerDataSnapshot.class));
            }
        }
        return result;
    }

    @Override
    public void saveAll(Map<UUID, PlayerDataSnapshot> snapshots) throws Exception {
        Files.createDirectories(file.getParent());
        TreeMap<String, PlayerDataSnapshot> ordered = new TreeMap<>();
        snapshots.forEach((id, snapshot) -> ordered.put(id.toString(), snapshot));
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, gson.toJson(ordered));
        atomicReplace(tmp, file);
    }

    static void atomicReplace(Path tmp, Path target) throws Exception {
        try { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
