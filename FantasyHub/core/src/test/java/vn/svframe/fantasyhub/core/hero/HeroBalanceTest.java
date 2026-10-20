package vn.svframe.fantasyhub.core.hero;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HeroBalanceTest {
  @Test void multipleProjectilesAndRecastShareOneTargetBudget() {
    var target=UUID.randomUUID();var budget=new HeroCastBudget(0.25);
    double applied=0;
    for(int i=0;i<12;i++) {double next=budget.allowed(target,100,7);budget.record(target,next);applied+=next;}
    assertEquals(25,applied,1e-9);
    assertEquals(0,budget.allowed(target,100,99));
    assertEquals(7,budget.allowed(UUID.randomUUID(),100,7));
  }
  @Test void aDamagedTargetCannotResetTheCastCeilingByHealingOrGainingMaximumHp() {
    var target=UUID.randomUUID();var budget=new HeroCastBudget(.4);
    budget.record(target,budget.allowed(target,100,30));
    assertEquals(10,budget.allowed(target,200,100));
  }
  @Test void ultimateCoefficientsAreNormalizedBeforeTheSharedCombatPipeline() {
    assertEquals(3,HeroBalance.coefficient(7.8,7.8,true,true),1e-9);
    assertEquals(3,HeroBalance.coefficient(4.2,4.2,true,true),1e-9);
    assertEquals(7.8,HeroBalance.coefficient(7.8,7.8,true,false),1e-9);
    assertEquals(.3* .65,HeroBalance.coefficient(.3,2.4,false,true),1e-9);
  }
  @Test void controlsFromDifferentAttackersUseOneTargetChain() {
    var target=UUID.randomUUID();var dr=new HeroControlDiminishingReturns();
    assertEquals(30,dr.duration(target,0,60,true));
    assertEquals(15,dr.duration(target,30,60,true));
    assertEquals(7,dr.duration(target,45,60,true));
    assertEquals(0,dr.duration(target,52,60,true));
    assertEquals(30,dr.duration(target,253,60,true));
  }
  @Test void pveControlPreservesTheBriefAndInvalidNumbersAreRejected() {
    var dr=new HeroControlDiminishingReturns();
    assertEquals(60,dr.duration(UUID.randomUUID(),0,60,false));
    assertThrows(IllegalArgumentException.class,()->new HeroCastBudget(Double.NaN));
    assertThrows(IllegalArgumentException.class,()->HeroBalance.coefficient(Double.NaN,1,false,true));
    assertThrows(IllegalArgumentException.class,()->HeroBalance.coefficient(1,Double.POSITIVE_INFINITY,true,true));
  }
  @Test void configuredPvpMultipliersAndControlLimitsAreActuallyUsed() {
    var rules=new HeroBalance.Rules(.4,2);
    assertEquals(.4,HeroBalance.coefficient(1,1,false,true,rules),1e-9);
    assertEquals(2,HeroBalance.coefficient(7.8,7.8,true,true,rules),1e-9);
    var dr=new HeroControlDiminishingReturns(20,100);var target=UUID.randomUUID();
    assertEquals(20,dr.duration(target,0,60,true));
    assertEquals(10,dr.duration(target,20,60,true));
    assertEquals(20,dr.duration(target,121,60,true));
  }

}
