package io.lumine.mythic.lib.player.skillmod;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.modifier.ModifierMap;
import io.lumine.mythic.lib.skill.Skill;
import io.lumine.mythic.lib.skill.handler.SkillHandler;

/** Skill-parameter modifier aggregation matching MythicLib 1.7.1 ordering. */
public class SkillModifierMap extends ModifierMap<SkillModifier> {
    public SkillModifierMap(MMOPlayerData playerData) { super(playerData); }
    public double calculateValue(Skill skill,String parameter){return calculateValue(skill.getHandler(),skill.getParameter(parameter),parameter);}
    public double calculateValue(SkillHandler<?> handler,double value,String parameter){double additive=1d,relative=1d;for(SkillModifier modifier:modifiers.values()){if(!modifier.getSkills().contains(handler)||!modifier.getParameter().equals(parameter))continue;switch(modifier.getType()){case FLAT->value+=modifier.getValue();case ADDITIVE_MULTIPLIER->additive+=modifier.getValue()/100d;case RELATIVE->relative*=1d+modifier.getValue()/100d;}}return value*additive*relative;}
}
