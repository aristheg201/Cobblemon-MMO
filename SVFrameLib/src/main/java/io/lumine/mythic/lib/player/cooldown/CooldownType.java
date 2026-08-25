package io.lumine.mythic.lib.player.cooldown;
public enum CooldownType implements CooldownObject {DODGE,PARRY,BLOCK,WEAPON_CRIT,SKILL_CRIT;@Override public String getCooldownPath(){return name();}}
