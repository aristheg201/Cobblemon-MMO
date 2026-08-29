package vn.svframe.svframemmo.gui;

import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframelib.module.MMOPlugin;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.PlayerClass;
import vn.svframe.svframemmo.config.DefaultFiles;
import vn.svframe.svframemmo.gui.skilltree.SkillTreeViewer;
import vn.svframe.svframemmo.skilltree.SkillTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads and owns the seven MMOCore RPG inventories plus the specific-skill-tree layout variant. */
public final class RpgGuiManager {
    private static final MMOPlugin GUI_PLUGIN = new MMOPlugin() {
        @Override public String getNamespacedKey() { return SVFrameMMO.ID; }
    };

    private volatile ClassSelect classSelect;
    private volatile SubclassSelect subclassSelect;
    private volatile AttributeView attributeView;
    private volatile PlayerStats playerStats;
    private volatile SkillList skillList;
    private volatile SkillTreeViewer skillTree;
    private final Map<String, ClassConfirmation> confirmations = new LinkedHashMap<>();
    private final Map<String, SkillTreeViewer> specificTrees = new LinkedHashMap<>();

    public synchronized void reload() throws IOException {
        ClassSelect nextClass = new ClassSelect();
        nextClass.reload(GUI_PLUGIN, load("gui/class-select.yml"));
        SubclassSelect nextSubclass = new SubclassSelect();
        nextSubclass.reload(GUI_PLUGIN, load("gui/subclass-select.yml"));
        AttributeView nextAttributes = new AttributeView();
        nextAttributes.reload(GUI_PLUGIN, load("gui/attribute-view.yml"));
        PlayerStats nextStats = new PlayerStats();
        nextStats.reload(GUI_PLUGIN, load("gui/player-stats.yml"));
        SkillList nextSkills = new SkillList();
        nextSkills.reload(GUI_PLUGIN, load("gui/skill-list.yml"));
        SkillTreeViewer nextTree = new SkillTreeViewer();
        nextTree.reload(GUI_PLUGIN, load("gui/skill-tree.yml"));

        Map<String, ClassConfirmation> nextConfirm = new LinkedHashMap<>();
        for (PlayerClass playerClass : SVFrameMMO.classes().getAll()) {
            String id = GuiSupport.normalizeId(playerClass.getId());
            Path override = DefaultFiles.ROOT.resolve("gui/class-confirm/class-confirm-" + id + ".yml");
            Map<String, Object> config = Files.isRegularFile(override) ? YamlLite.map(YamlLite.parse(override)) : load("gui/class-confirm/class-confirm-default.yml");
            ClassConfirmation confirmation = new ClassConfirmation(playerClass, !Files.isRegularFile(override));
            confirmation.reload(GUI_PLUGIN, config);
            nextConfirm.put(playerClass.getId(), confirmation);
        }

        Map<String, SkillTreeViewer> nextSpecific = new LinkedHashMap<>();
        for (SkillTree tree : SVFrameMMO.skillTrees().getAll()) {
            String id = GuiSupport.normalizeId(tree.getId());
            Path override = DefaultFiles.ROOT.resolve("gui/specific-skill-tree/specific-skill-tree-" + id + ".yml");
            Map<String, Object> config = Files.isRegularFile(override) ? YamlLite.map(YamlLite.parse(override)) : load("gui/specific-skill-tree/specific-skill-tree-default.yml");
            SkillTreeViewer viewer = new SkillTreeViewer(tree, !Files.isRegularFile(override));
            viewer.reload(GUI_PLUGIN, config);
            nextSpecific.put(tree.getId(), viewer);
        }

        classSelect = nextClass;
        subclassSelect = nextSubclass;
        attributeView = nextAttributes;
        playerStats = nextStats;
        skillList = nextSkills;
        skillTree = nextTree;
        confirmations.clear(); confirmations.putAll(nextConfirm);
        specificTrees.clear(); specificTrees.putAll(nextSpecific);
        PlayerProfileInteractionRuntime.install();
    }

    private static Map<String, Object> load(String relative) throws IOException {
        Path path = DefaultFiles.ROOT.resolve(relative);
        if (!Files.isRegularFile(path)) throw new IOException("Missing GUI configuration: " + path);
        return YamlLite.map(YamlLite.parse(path));
    }

    public ClassSelect classSelect() { return require(classSelect, "class-select"); }
    public SubclassSelect subclassSelect() { return require(subclassSelect, "subclass-select"); }
    public AttributeView attributeView() { return require(attributeView, "attribute-view"); }
    public PlayerStats playerStats() { return require(playerStats, "player-stats"); }
    public SkillList skillList() { return require(skillList, "skill-list"); }
    public SkillTreeViewer skillTree() { return require(skillTree, "skill-tree"); }
    public ClassConfirmation confirmation(PlayerClass target) {
        ClassConfirmation found = confirmations.get(target.getId());
        if (found == null) throw new IllegalStateException("No class confirmation GUI for " + target.getId());
        return found;
    }
    public SkillTreeViewer specificTree(SkillTree tree) {
        SkillTreeViewer found = specificTrees.get(tree.getId());
        if (found == null) throw new IllegalStateException("No specific skill tree GUI for " + tree.getId());
        return found;
    }

    public void openClassSelect(PlayerData data) { classSelect().newInventory(data).open(); }
    public void openSubclassSelect(PlayerData data) { subclassSelect().newInventory(data).open(); }
    public void openAttributes(PlayerData data) { attributeView().newInventory(data).open(); }
    public void openStats(PlayerData data) { playerStats().newInventory(data).open(); }
    public void openSkills(PlayerData data) { skillList().newInventory(data).open(); }
    public void openSkillTree(PlayerData data) { skillTree().newInventory(data).open(); }
    public void openSkillTree(PlayerData data, SkillTree tree) { specificTree(tree).newInventory(data).open(); }

    private static <T> T require(T value, String id) {
        if (value == null) throw new IllegalStateException("RPG GUI manager has not loaded " + id);
        return value;
    }
}
