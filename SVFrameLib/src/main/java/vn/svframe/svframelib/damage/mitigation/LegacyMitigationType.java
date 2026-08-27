package vn.svframe.svframelib.damage.mitigation;

import vn.svframe.svframelib.api.event.DamageMitigationEvent;
import vn.svframe.svframelib.api.event.mitigation.PlayerBlockEvent;
import vn.svframe.svframelib.api.event.mitigation.PlayerDodgeEvent;
import vn.svframe.svframelib.api.event.mitigation.PlayerParryEvent;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.damage.AttackMetadata;

@Deprecated
public enum LegacyMitigationType {
    BLOCK, PARRY, DODGE;

    public DamageMitigationEvent generateLegacyEvent(MMOPlayerData playerData, AttackMetadata attack, MitigationType type) {
        return switch (this) {
            case BLOCK -> new PlayerBlockEvent(playerData, attack, type);
            case PARRY -> new PlayerParryEvent(playerData, attack, type);
            case DODGE -> new PlayerDodgeEvent(playerData, attack, type);
        };
    }
}
