package vn.svframe.svframelib.api.event.mitigation;

import vn.svframe.svframelib.api.event.DamageMitigationEvent;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.mitigation.MitigationType;

@Deprecated
public class PlayerBlockEvent extends DamageMitigationEvent {
    private double power;
    public PlayerBlockEvent(MMOPlayerData player, AttackMetadata attack, MitigationType type) { super(player, type, attack); }
    public double getPower() { return power; }
    public double getDamageBlocked() { return power * getAttack().getDamage().getDamage(); }
    public void setPower(double power) { if (power < 0d || power > 1d) throw new IllegalArgumentException("Block power must be between 0 and 1"); this.power = power; }
}
