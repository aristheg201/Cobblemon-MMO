package vn.svframe.svframemmo.cobblemon.gameplay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PokemonGameplayTest {
  @Test
  void battleVictoryRewardsEachTrainerOnceRegardlessOfPartySize() {
    var ledger = new PokemonRewardLedger();
    UUID event = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
    assertTrue(ledger.claimVictory(event, first));
    for (int i = 0; i < 6; i++) assertFalse(ledger.claimVictory(event, first));
    assertTrue(ledger.claimVictory(event, second));
  }

  @Test
  void defeatCannotRewardBothBattleFaintAndOverworldDeath() {
    var ledger = new PokemonRewardLedger();
    UUID pokemon = UUID.randomUUID();
    ledger.markBattlePokemon(pokemon);
    assertFalse(ledger.claimDefeat(pokemon));
    UUID wild = UUID.randomUUID();
    assertTrue(ledger.claimDefeat(wild));
    assertFalse(ledger.claimDefeat(wild));
  }

  @Test
  void rewardFormulaHasSeparateCapsAndRejectsInvalidNumbers() {
    var config = new PokemonGameplayConfig();
    assertEquals(70, config.battle.amount(10));
    assertEquals(35, config.overworld.amount(10));
    assertEquals(250, config.overworld.amount(10000));
    config.overworld.perLevel = Double.NaN;
    assertThrows(IllegalArgumentException.class, config::validate);
  }

  @Test
  void timingConfigRejectsUnboundedWindupsAndCooldowns() {
    var config = new PokemonGameplayConfig();
    config.hostility.cooldownTicks = Integer.MAX_VALUE;
    assertThrows(IllegalArgumentException.class, config::validate);
  }

  @Test
  void ownershipPersistsAcrossProtectedStatesToPreventFofFallbackAttacks() {
    assertTrue(HostilityRules.claims(true, false, false, false, false));
    assertFalse(HostilityRules.claims(true, true, false, false, false));
    assertFalse(HostilityRules.claims(true, false, true, false, false));
    assertFalse(HostilityRules.claims(true, false, false, true, false));
    assertFalse(HostilityRules.claims(true, false, false, false, true));
    assertFalse(HostilityRules.canAttack(true, true, false, false, false, false));
    assertFalse(HostilityRules.canAttack(true, false, true, false, false, false));
    assertFalse(HostilityRules.canAttack(true, false, false, true, false, false));
    assertFalse(HostilityRules.canAttack(true, false, false, false, true, false));
    assertFalse(HostilityRules.canAttack(true, false, false, false, false, true));
    assertTrue(HostilityRules.canAttack(true, false, false, false, false, false));
  }

  @Test
  void playerPursuitLimitReleasesCapacityWhenPokemonDisengages() {
    var slots = new PursuitSlots();
    UUID player = UUID.randomUUID();
    UUID[] attackers = new UUID[5];
    for (int i = 0; i < 5; i++) attackers[i] = UUID.randomUUID();
    for (int i = 0; i < 4; i++) assertTrue(slots.claim(attackers[i], player, 4));
    assertFalse(slots.claim(attackers[4], player, 4));
    assertTrue(slots.claim(attackers[0], player, 4));
    slots.release(attackers[1]);
    assertTrue(slots.claim(attackers[4], player, 4));
  }

  @Test
  void phaseDamageMultiplierRejectsNonfiniteNegativeAndExcessiveValuesBeforeDamage() {
    for (double invalid :
        new double[] {
          Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -.01, 20.01
        }) {
      assertThrows(
          IllegalArgumentException.class, () -> PokemonOverworldCombat.strike(null, null, invalid));
    }
    assertFalse(PokemonOverworldCombat.strike(null, null, 0));
    assertFalse(PokemonOverworldCombat.strike(null, null, 1));
    assertFalse(PokemonOverworldCombat.strike(null, null, 20));
  }
}
