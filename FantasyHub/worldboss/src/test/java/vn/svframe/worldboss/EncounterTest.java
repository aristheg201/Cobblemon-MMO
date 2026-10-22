package vn.svframe.worldboss;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class EncounterTest {
 @Test void transitionsAndValidParticipantRewards(){
  var e=new Encounter("test","pokemon","arena",1000,1000,60,600);var a=UUID.randomUUID();var afk=UUID.randomUUID();
  assertEquals(Encounter.State.SETUP,e.state);assertFalse(e.fight(1059));assertTrue(e.fight(1060));
  e.damage(a,4);e.presence(a,31);e.presence(afk,200);assertFalse(e.eligible(a,.005,30));
  e.damage(a,1);assertTrue(e.eligible(a,.005,30));assertFalse(e.eligible(afk,.005,30));
  assertTrue(e.finish(true));assertFalse(e.finish(false));assertEquals(12,e.rewards(12,3,.005,30).get(a));
 }
 @Test void idleTimeBeforeFirstDamageCannotQualify(){
  var e=new Encounter("e","b","a",0,1000,0,600);var p=UUID.randomUUID();e.fight(0);e.presence(p,300);e.damage(p,5);assertFalse(e.eligible(p,.005,30));e.presence(p,30);assertTrue(e.eligible(p,.005,30));
 }
 @Test void rejectedDamageNeverCreatesAContributor(){
  var e=new Encounter("e","b","a",0,1000,60,600);var p=UUID.randomUUID();e.damage(p,50);assertTrue(e.contributions.isEmpty());e.fight(60);e.damage(p,Double.NaN);e.damage(p,Double.POSITIVE_INFINITY);e.damage(p,-50);assertTrue(e.contributions.isEmpty());
 }
 @Test void timeoutAndAbortHaveDifferentRewards(){
  var p=UUID.randomUUID();var e=new Encounter("e","b","a",0,1000,0,600);e.fight(0);e.damage(p,5);e.presence(p,30);
  assertFalse(e.expired(599));assertTrue(e.expired(600));e.finish(false);assertEquals(3,e.rewards(12,3,.005,30).get(p));
  var aborted=new Encounter("a","b","a",0,1000,0,600);aborted.fight(0);aborted.damage(p,50);aborted.presence(p,30);aborted.abort();assertTrue(aborted.rewards(12,3,.005,30).isEmpty());
 }
 @Test void fastWinsRewardRealContributorsWithoutWaitingThirtySeconds(){
  var p=UUID.randomUUID();var afk=UUID.randomUUID();var instant=new Encounter("instant","b","a",0,1000,0,600);instant.fight(0);instant.damage(p,1000,0);instant.presence(afk,30);instant.finish(true,0);assertEquals(12,instant.rewards(12,3,.005,30).get(p));assertFalse(instant.rewards(12,3,.005,30).containsKey(afk));
  var ten=new Encounter("ten","b","a",0,1000,0,600);ten.fight(0);ten.damage(p,5,0);ten.presence(p,10);ten.finish(true,10);assertEquals(12,ten.rewards(12,3,.005,30).get(p));
 }
 @Test void quickWinCreditsOnlyTheAvailablePostContributionWindow(){
  var p=UUID.randomUUID();var e=new Encounter("e","b","a",0,1000,0,600);e.fight(0);e.damage(p,5,9);e.presence(p,10);e.finish(true,10);assertEquals(12,e.rewards(12,3,.005,30).get(p));
 }
 @Test void lossesKeepThirtySecondRequirementAndFightStartsAtActualSpawn(){
  var p=UUID.randomUUID();var e=new Encounter("e","b","a",0,1000,60,600);e.fight(65);assertEquals(65,e.fightAt);assertEquals(665,e.deadline);e.damage(p,5,65);e.presence(p,10);e.finish(false,75);assertTrue(e.rewards(12,3,.005,30).isEmpty());
 }
 @Test void secondPrecisionScheduleFiresOnlyItsSecondAndNeverBackfills(){
  var schedule=new BossSchedule("Asia/Ho_Chi_Minh",List.of("12:00:30"));var at=Instant.parse("2026-10-21T05:00:30Z");assertTrue(schedule.due(at.minusSeconds(1),Set.of()).isEmpty());var due=schedule.due(at,Set.of());assertEquals(1,due.size());assertTrue(schedule.due(at.plusSeconds(1),Set.of()).isEmpty());assertTrue(schedule.due(at,Set.copyOf(due)).isEmpty());assertEquals("2026-10-22T12:00:30+07:00[Asia/Ho_Chi_Minh]",schedule.next(at.plusSeconds(1)).toString());
 }
 @Test void scheduleUsesZoneAndNoCatchupOrDuplicate(){
  var schedule=new BossSchedule("Asia/Ho_Chi_Minh",List.of("12:00","18:00","21:00"));var now=Instant.parse("2026-10-21T05:00:30Z");var done=new HashSet<String>();
  var due=schedule.due(now,done);assertEquals(1,due.size());done.addAll(due);assertTrue(schedule.due(now.plusSeconds(10),done).isEmpty());
  assertTrue(schedule.due(now.plusSeconds(60),Set.of()).isEmpty());assertEquals("2026-10-21T12:00+07:00[Asia/Ho_Chi_Minh]",schedule.next(now).toString());
 }
 @Test void settlementClosesOnlyAfterEveryPlayerDelivery() throws Exception {
  var file=java.nio.file.Files.createTempDirectory("boss-settlement").resolve("state.json");var ledger=BossLedger.load(file);var one=UUID.randomUUID();var two=UUID.randomUUID();
  var e=new Encounter("e","b","a",0,1000,0,600);e.fight(0);e.damage(one,50);e.damage(two,50);e.presence(one,30);e.presence(two,30);e.finish(true);ledger.encounters.put(e.id,e);ledger.settle(e,12,3,.005,30);ledger.save();
  assertEquals(Encounter.State.SETTLEMENT,e.state);ledger.delivered(e.id,one);assertEquals(Encounter.State.SETTLEMENT,e.state);ledger.save();ledger=BossLedger.load(file);ledger.delivered(e.id,two);assertEquals(Encounter.State.CLOSED,ledger.encounters.get(e.id).state);assertEquals(Encounter.State.WON,ledger.encounters.get(e.id).outcome);
 }
 @Test void recoveryAbortsLiveEncountersAndKeepsPendingSettlement() throws Exception {
  var file=java.nio.file.Files.createTempDirectory("boss-recovery").resolve("state.json");var ledger=BossLedger.load(file);var live=new Encounter("live","b","a",0,1000,0,600);live.fight(0);ledger.encounters.put(live.id,live);var id=UUID.randomUUID();ledger.queue("done",Map.of(id,12));ledger.save();ledger=BossLedger.load(file);ledger.recover();assertEquals(Encounter.State.ABORTED,ledger.encounters.get("live").state);assertEquals(1,ledger.pending(id).size());
 }
 @Test void rewardMailboxSurvivesReloadAndRetriesOnlyPending() throws Exception {
  var path=java.nio.file.Files.createTempDirectory("boss-ledger").resolve("state.json");var ledger=BossLedger.load(path);var id=UUID.randomUUID();
  ledger.queue("encounter",Map.of(id,12));ledger.queue("encounter",Map.of(id,12));ledger.save();
  ledger=BossLedger.load(path);assertEquals(1,ledger.pending(id).size());assertEquals(12,ledger.pending(id).getFirst().amount);ledger.delivered("encounter",id);ledger.save();assertTrue(BossLedger.load(path).pending(id).isEmpty());
 }
 @Test void genuineLateContributorCanQualifyForFastVictoryWithoutBorrowingAfkTime(){
  var p=UUID.randomUUID();var e=new Encounter("quick","b","a",0,1000,0,600);e.fight(0);e.presence(p,10);e.damage(p,5,2);e.presence(p,8);e.finish(true,10);assertTrue(e.eligible(p,.005,30));
  var longFight=new Encounter("long","b","a",0,1000,0,600);longFight.fight(0);longFight.damage(p,5,55);longFight.presence(p,5);longFight.finish(true,60);assertFalse(longFight.eligible(p,.005,30));
 }
}
