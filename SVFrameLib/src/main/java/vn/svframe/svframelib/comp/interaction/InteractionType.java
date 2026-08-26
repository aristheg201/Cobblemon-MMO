package vn.svframe.svframelib.comp.interaction;
public enum InteractionType { OFFENSE_SKILL, SUPPORT_SKILL, OFFENSE_ACTION, SUPPORT_ACTION;
 public boolean isSkill(){return this==OFFENSE_SKILL||this==SUPPORT_SKILL;} public boolean isOffense(){return this==OFFENSE_SKILL||this==OFFENSE_ACTION;}}
