package vn.svframe.svframelib.script.mechanic;

import vn.svframe.mythiclibfabric.MythicLibFabricMod;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframelib.script.mechanic.offense.DamageMechanic;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.SkillMetadataContextBridge;

import java.util.List;
import java.util.Locale;

/** Runtime-backed mechanic for mechanics not yet represented by a dedicated native class. */
public class RawMechanic extends Mechanic {
    private final String raw;
    public RawMechanic(String raw) { this.raw = raw; }

    @Override
    public void cast(SkillMetadata metadata) {
        MMOLineConfig config = new MMOLineConfig(raw);
        String type = config.getKey() == null ? "" : config.getKey().trim().toLowerCase(Locale.ROOT);
        // Damage must run through AttackMetadata/DamageManager, never through generic vanilla damage.
        if (type.equals("damage")) {
            new DamageMechanic(config).cast(metadata);
            return;
        }
        MythicLibFabricMod.castInline(List.of(raw), SkillMetadataContextBridge.context(metadata));
    }

    public String raw() { return raw; }
}
