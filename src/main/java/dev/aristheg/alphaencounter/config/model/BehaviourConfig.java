package dev.aristheg.alphaencounter.config.model;

public final class BehaviourConfig {
    public String id = "passive";
    public boolean aggressive = false;
    public double aggroRadius = 32.0;
    public double leashRadius = 64.0;
    public double chaseSpeed = 1.0;
    public double fieldAttackRange = 3.0;
    public double fieldAttackDamage = 6.0;
    public int fieldAttackCooldownTicks = 24;
    public double fieldAttackKnockback = 0.35;
    public String fieldAttackAnimation = "physical";
    public int reengageCooldownTicks = 60;

    public void normalize(String fallbackId) {
        if (id == null || id.isBlank()) id = fallbackId;
        aggroRadius = Math.max(4.0, aggroRadius);
        leashRadius = Math.max(aggroRadius, leashRadius);
        chaseSpeed = Math.max(0.1, chaseSpeed);
        fieldAttackRange = Math.max(1.0, fieldAttackRange);
        fieldAttackDamage = Math.max(0.0, fieldAttackDamage);
        fieldAttackCooldownTicks = Math.max(1, fieldAttackCooldownTicks);
        fieldAttackKnockback = Math.max(0.0, fieldAttackKnockback);
        if (fieldAttackAnimation == null) fieldAttackAnimation = "";
        reengageCooldownTicks = Math.max(0, reengageCooldownTicks);
    }
}
