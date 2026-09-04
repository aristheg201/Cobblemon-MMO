package vn.svframe.svframemmo.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Persists whether a player has completed the mandatory initial class selection. */
public final class ClassSelectionRuntime {
    private static final Type UUID_SET = new TypeToken<Set<UUID>>() { }.getType();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Set<UUID> chosen = new LinkedHashSet<>();
    private Path file;

    public synchronized void start(MinecraftServer server, Collection<PlayerData> existingData) {
        file = server.getSavePath(WorldSavePath.ROOT).resolve("svframemmo-class-selection.json");
        chosen.clear();
        if (Files.isRegularFile(file)) load();
        else {
            // Migration rule: players already present in the native persistence store predate this flag.
            // Treat them as having completed selection so enabling parity does not trap an existing server population.
            for (PlayerData data : existingData) chosen.add(data.getUniqueId());
            save();
        }
    }

    public void onJoin(PlayerData data) {
        if (!SVFrameMMO.config().forceClassSelection() || data == null || !data.isOnline()) return;
        if (!data.getProfess().hasOption(vn.svframe.svframemmo.api.player.profess.ClassOption.DEFAULT)) {
            markChosen(data);
            return;
        }
        if (hasChosen(data.getUniqueId())) return;
        SVFrameMMO.delayedActions().schedule(SVFrameMMO.currentTick() + 1L, () -> {
            if (data.isOnline() && SVFrameMMO.config().forceClassSelection() && !hasChosen(data.getUniqueId()))
                SVFrameMMO.gui().classSelect().newInventory(data, true).open();
        });
    }

    public synchronized boolean hasChosen(UUID player) { return chosen.contains(player); }
    public boolean hasChosen(PlayerData data) { return data != null && hasChosen(data.getUniqueId()); }

    public synchronized void markChosen(PlayerData data) {
        if (data != null && chosen.add(data.getUniqueId())) save();
    }

    public synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(chosen, UUID_SET));
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not save class-selection state", exception);
        }
    }

    private void load() {
        try (Reader reader = Files.newBufferedReader(file)) {
            Set<UUID> loaded = gson.fromJson(reader, UUID_SET);
            if (loaded != null) chosen.addAll(loaded);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load class-selection state", exception);
        }
    }
}
