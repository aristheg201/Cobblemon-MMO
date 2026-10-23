package vn.svframe.svframemmo.cobblemon.gameplay;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class TemperamentRulesTest {
 @Test void defaultNeutralAndAlphaHostile(){var c=new TemperamentRules();assertEquals(TemperamentRules.Kind.NEUTRAL,c.resolve("eevee","normal",10,"hardy","minecraft:overworld",false,0,64,0));assertEquals(TemperamentRules.Kind.HOSTILE,c.resolve("eevee","normal",10,"hardy","minecraft:overworld",true,0,64,0));}
 @Test void explicitSpeciesOverrideWinsOverAlpha(){var c=new TemperamentRules();var r=new TemperamentRules.Rule();r.species="eevee";r.temperament=TemperamentRules.Kind.FRIENDLY;c.overrides.add(r);assertEquals(TemperamentRules.Kind.FRIENDLY,c.resolve("eevee","normal",99,"hardy","minecraft:overworld",true,0,64,0));}
 @Test void allCriteriaMustMatchIncludingRegion(){var c=new TemperamentRules();var r=new TemperamentRules.Rule();r.species="eevee";r.form="alola";r.minLevel=20;r.nature="brave";r.dimension="minecraft:overworld";r.region=new double[]{0,0,0,10,100,10};r.temperament=TemperamentRules.Kind.FRIENDLY;c.overrides.add(r);assertEquals(TemperamentRules.Kind.FRIENDLY,c.resolve("eevee","alola",20,"brave","minecraft:overworld",false,5,64,5));assertEquals(TemperamentRules.Kind.NEUTRAL,c.resolve("eevee","alola",19,"brave","minecraft:overworld",false,5,64,5));}
 @Test void rpgHpFormulaAndCap(){assertEquals(234,RpgHealthRules.maximum(50,20,false,1.5,1200));assertEquals(351,RpgHealthRules.maximum(50,20,true,1.5,1200));assertEquals(1200,RpgHealthRules.maximum(255,100,true,1.5,1200));}
}
