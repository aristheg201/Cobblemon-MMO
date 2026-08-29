package vn.svframe.svframemmo.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** File-per-player YAML backend equivalent to the original userdata storage model. */
public final class YamlPlayerDataStore implements PlayerDataStore {
    private final Path directory;
    private final YamlSnapshotCodec codec = new YamlSnapshotCodec();

    public YamlPlayerDataStore(Path directory) { this.directory = directory; }
    @Override public String id() { return "YAML"; }
    public Path directory() { return directory; }

    @Override
    public Map<UUID, PlayerDataSnapshot> loadAll() throws Exception {
        LinkedHashMap<UUID, PlayerDataSnapshot> result = new LinkedHashMap<>();
        if (!Files.isDirectory(directory)) return result;
        try (var stream = Files.list(directory)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                String name = file.getFileName().toString();
                UUID id = UUID.fromString(name.substring(0, name.length() - 4));
                result.put(id, codec.decode(Files.readString(file)));
            }
        }
        return result;
    }

    @Override
    public void saveAll(Map<UUID, PlayerDataSnapshot> snapshots) throws Exception {
        Files.createDirectories(directory);
        Set<String> expected = snapshots.keySet().stream().map(id -> id + ".yml").collect(Collectors.toSet());
        for (Map.Entry<UUID, PlayerDataSnapshot> entry : snapshots.entrySet()) {
            Path target = directory.resolve(entry.getKey() + ".yml");
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, codec.encode(entry.getValue()));
            JsonPlayerDataStore.atomicReplace(tmp, target);
        }
        try (var stream = Files.list(directory)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".yml")).toList())
                if (!expected.contains(file.getFileName().toString())) Files.deleteIfExists(file);
        }
    }
}
