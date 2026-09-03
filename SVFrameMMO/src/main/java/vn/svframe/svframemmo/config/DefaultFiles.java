package vn.svframe.svframemmo.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Installs bundled defaults without overwriting server edits, plus narrowly-scoped legacy migrations. */
public final class DefaultFiles {
    public static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("SVFrameMMO").toAbsolutePath().normalize();
    private static final List<String> FILES = List.of(
            "config.yml", "messages.yml", "stats.yml", "exp-sources.yml", "restrictions.yml", "items.yml",
            "classes/human.yml", "classes/marksman.yml", "classes/paladin.yml", "classes/rogue.yml", "classes/warrior.yml",
            "classes/mage/mage.yml", "classes/mage/arcane-mage.yml",
            "attributes/default_attributes.yml",
            "skills/ambers.yml", "skills/neptune-gift.yml", "skills/sneaky-picky.yml", "skills/staff-attack.yml", "skills/legacy-class-aliases.yml",
            "professions/alchemy.yml", "professions/enchanting.yml", "professions/farming.yml", "professions/fishing.yml",
            "professions/mining.yml", "professions/smelting.yml", "professions/smithing.yml", "professions/woodcutting.yml",
            "exp-tables/default.yml", "exp-curves/levels.txt",
            "skill-trees/combat.yml", "skill-trees/general.yml", "skill-trees/loop.yml", "skill-trees/mage-arcane-mage.yml",
            "skill-trees/rogue-marksman.yml", "skill-trees/warrior-paladin.yml",
            "gui/class-select.yml", "gui/class-confirm/class-confirm-default.yml", "gui/subclass-select.yml", "gui/attribute-view.yml",
            "gui/player-stats.yml", "gui/skill-list.yml", "gui/skill-tree.yml", "gui/specific-skill-tree/specific-skill-tree-default.yml");

    private DefaultFiles() { }

    public static void ensure() throws IOException {
        Files.createDirectories(ROOT);
        ClassLoader loader = DefaultFiles.class.getClassLoader();
        for (String relative : FILES) {
            Path output = ROOT.resolve(relative).normalize();
            if (!output.startsWith(ROOT)) throw new IOException("Unsafe default path: " + relative);
            if (Files.exists(output)) continue;
            try (InputStream input = loader.getResourceAsStream("defaults/" + relative)) {
                if (input == null) throw new IOException("Missing bundled SVFrameMMO default: " + relative);
                Files.createDirectories(output.getParent());
                Path temporary = Files.createTempFile(output.getParent(), output.getFileName().toString(), ".tmp");
                try {
                    Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                    moveReplacing(temporary, output);
                } finally { Files.deleteIfExists(temporary); }
            }
        }
        migrateLegacyHealthCap();
        migrateLegacyHealthHudComment();
    }

    /** Removes only the exact 20/0/40 MAX_HEALTH block shipped by the broken Fabric port. */
    private static void migrateLegacyHealthCap() throws IOException {
        Path stats = ROOT.resolve("stats.yml");
        if (!Files.isRegularFile(stats)) return;
        String text = Files.readString(stats, StandardCharsets.UTF_8);
        String legacy = "    MAX_HEALTH:\n        base: 20\n        per-level: 0\n        max: 40\n";
        if (!text.contains(legacy)) return;
        String migrated = text.replace(legacy, "    MAX_HEALTH:\n        base: 20\n        per-level: 0\n");
        writeReplacing(stats, migrated, "stats.yml");
    }

    /** Rewrites only the obsolete comment which incorrectly described the HUD cap as authoritative. */
    private static void migrateLegacyHealthHudComment() throws IOException {
        Path config = ROOT.resolve("config.yml");
        if (!Files.isRegularFile(config)) return;
        String text = Files.readString(config, StandardCharsets.UTF_8);
        String legacy = "# max-vanilla-health is the REAL MAX_HEALTH cap, not only display text.\n"
                + "# 40 HP = 20 hearts = at most two vanilla heart rows.\n";
        if (!text.contains(legacy)) return;
        String replacement = "# max-vanilla-health is a VISUAL-ONLY cap for vanilla heart rendering.\n"
                + "# Authoritative MAX_HEALTH/current health remain uncapped; the numeric HUD shows real values.\n"
                + "# 40 visible HP = 20 hearts = at most two vanilla heart rows.\n";
        writeReplacing(config, text.replace(legacy, replacement), "config.yml");
    }

    private static void writeReplacing(Path target, String content, String prefix) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), prefix, ".migration.tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            moveReplacing(temporary, target);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void moveReplacing(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
}
