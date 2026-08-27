package vn.svframe.svframelib.script;

import vn.svframe.svframelib.script.mechanic.Mechanic;
import vn.svframe.svframelib.script.mechanic.RawMechanic;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import vn.svframe.svframelib.fabric.runtime.script.ExpressionRuntime;
import vn.svframe.svframelib.fabric.runtime.script.ScriptLineParser;

import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MechanicQueue {
    private static final Logger LOG = Logger.getLogger("SVFrameLib");
    private final Iterator<Mechanic> queue;
    private final SkillMetadata metadata;
    private final Script script;
    private int counter;

    public MechanicQueue(SkillMetadata metadata, Script script) {
        this.metadata = metadata;
        this.script = script;
        this.queue = script.getMechanics().iterator();
    }

    public boolean next() {
        while (queue.hasNext()) {
            counter++;
            Mechanic mechanic = queue.next();
            if (mechanic instanceof RawMechanic raw) {
                ScriptLineParser.Call call = ScriptLineParser.parse(raw.raw());
                if (call.name().equals("delay")) {
                    long ticks = delayTicks(call);
                    SVFrameLibFabricMod.schedule((int) Math.min(Integer.MAX_VALUE, Math.max(1L, ticks)), this::next);
                    return false;
                }
            }
            try {
                mechanic.cast(metadata);
            } catch (RuntimeException exception) {
                LOG.log(Level.WARNING, "Could not execute mechanic n" + counter + " from script '" + script.getId() + "': " + exception.getMessage());
                return true;
            }
        }
        return true;
    }

    private long delayTicks(ScriptLineParser.Call call) {
        String expression = call.params().getOrDefault("amount", "0");
        try {
            var context = vn.svframe.svframelib.skill.SkillMetadataContextBridge.context(metadata);
            return (long) new ExpressionRuntime().evaluate(expression, context.numbers());
        } catch (RuntimeException ignored) {
            try { return (long) Double.parseDouble(expression); }
            catch (NumberFormatException invalid) { return 0L; }
        }
    }
}
