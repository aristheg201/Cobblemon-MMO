package vn.svframe.svframelib.script;

import vn.svframe.svframelib.MythicLib;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.script.condition.Condition;
import vn.svframe.svframelib.script.mechanic.Mechanic;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.PostLoadAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Native Script implementation which resolves lines through SkillManager like MythicLib 1.7.1. */
public class Script {
    private final String id;
    private final boolean pub;
    private final List<String> rawConditions;
    private final List<String> rawMechanics;
    private final List<Condition> conditions = new ArrayList<>();
    private final List<Mechanic> mechanics = new ArrayList<>();

    public Script(String id, List<String> mechanics) { this(id, false, List.of(), mechanics); }
    public Script(String id, boolean pub) { this(id, pub, List.of(), List.of()); }

    public Script(String id, boolean pub, List<String> conditions, List<String> mechanics) {
        this.id = Objects.requireNonNull(id, "Script id cannot be null");
        this.pub = pub;
        this.rawConditions = List.copyOf(conditions == null ? List.of() : conditions);
        this.rawMechanics = List.copyOf(mechanics == null ? List.of() : mechanics);
        loadLines();
    }

    private void loadLines() {
        this.conditions.clear();
        this.mechanics.clear();
        int index = 0;
        for (String line : rawConditions) {
            index++;
            try {
                this.conditions.add(MythicLib.plugin.getSkills().loadCondition(new MMOLineConfig(line)));
            } catch (RuntimeException exception) {
                MythicLib.plugin.getLogger().warning("Could not load condition '" + line + "' from script '" + id + "': " + exception.getMessage());
            }
        }
        index = 0;
        for (String line : rawMechanics) {
            index++;
            try {
                this.mechanics.add(MythicLib.plugin.getSkills().loadMechanic(new MMOLineConfig(line)));
            } catch (RuntimeException exception) {
                MythicLib.plugin.getLogger().warning("Could not load mechanic '" + line + "' from script '" + id + "': " + exception.getMessage());
            }
        }
    }

    public PostLoadAction getPostLoadAction() { return new PostLoadAction(ignored -> loadLines()); }
    public String getId() { return id; }
    public boolean isPublic() { return pub; }
    public List<Mechanic> getMechanics() { return List.copyOf(mechanics); }
    public List<Condition> getConditions() { return List.copyOf(conditions); }
    public boolean cast(MMOPlayerData data) { return cast(SkillMetadata.of(data)); }

    public boolean cast(SkillMetadata metadata) {
        int conditionCounter = 0;
        for (Condition condition : conditions) {
            try {
                conditionCounter++;
                if (!condition.checkIfMet(metadata)) return false;
            } catch (RuntimeException exception) {
                MythicLib.plugin.getLogger().warning("Could not check condition n" + conditionCounter + " from script '" + id + "': " + exception.getMessage());
                return false;
            }
        }
        new MechanicQueue(metadata, this).next();
        return true;
    }

    public List<String> rawConditions() { return rawConditions; }
    public List<String> rawMechanics() { return rawMechanics; }
}
