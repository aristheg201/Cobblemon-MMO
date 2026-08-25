package io.lumine.mythic.lib.script;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.script.condition.Condition;
import io.lumine.mythic.lib.script.mechanic.Mechanic;
import io.lumine.mythic.lib.script.condition.RawCondition;
import io.lumine.mythic.lib.script.mechanic.RawMechanic;
import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.util.PostLoadAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Script {
    private final String id;
    private final boolean pub;
    private final List<String> conditions;
    private final List<String> mechanics;

    public Script(String id, List<String> mechanics) { this(id, true, List.of(), mechanics); }
    public Script(String id, boolean pub) { this(id, pub, List.of(), List.of()); }
    public Script(String id, boolean pub, List<String> conditions, List<String> mechanics) {
        this.id = Objects.requireNonNull(id);
        this.pub = pub;
        this.conditions = List.copyOf(conditions);
        this.mechanics = List.copyOf(mechanics);
    }

    public PostLoadAction getPostLoadAction() { return new PostLoadAction(x -> {}); }
    public String getId() { return id; }
    public boolean isPublic() { return pub; }

    public List<Mechanic> getMechanics() {
        List<Mechanic> out = new ArrayList<>();
        for (String line : mechanics) out.add(new RawMechanic(line));
        return List.copyOf(out);
    }

    public List<Condition> getConditions() {
        List<Condition> out = new ArrayList<>();
        for (String line : conditions) out.add(new RawCondition(line));
        return List.copyOf(out);
    }

    public boolean cast(MMOPlayerData data) { return cast(SkillMetadata.of(data)); }

    public boolean cast(SkillMetadata metadata) {
        for (Condition condition : getConditions())
            if (!condition.checkIfMet(metadata)) return false;
        new MechanicQueue(metadata, this).next();
        return true;
    }

    public List<String> rawConditions() { return conditions; }
    public List<String> rawMechanics() { return mechanics; }
}
