package vn.svframe.svframelib.api.event.mitigation;

import vn.svframe.svframelib.api.event.DamageMitigationEvent;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.mitigation.MitigationType;

@Deprecated
public class PlayerParryEvent extends DamageMitigationEvent {
    public PlayerParryEvent(MMOPlayerData player, AttackMetadata attack, MitigationType type) { super(player, type, attack); }
}
