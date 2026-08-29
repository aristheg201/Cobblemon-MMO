package vn.svframe.svframemmo.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Installs immutable bundled defaults on first boot without overwriting server edits. */
public final class DefaultFiles {
    public static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("SVFrameMMO").toAbsolutePath().normalize();
    private static final List<String> FILES = List.of(
            "config.yml", "stats.yml", "exp-sources.yml", "restrictions.yml", "items.yml",
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
                    try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING); }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        }
    }
}
