package vn.svframe.worldboss;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class PersonalBossSettingsTest {
 @Test void scalesCopyAndPreservesScheduledStatsAndLoot(){var original=new BossConfig.Boss("thanos","Thanos","cosmic","THANOS");original.health=10000;original.damageMitigation=.85;original.stoneDropChance=.15;original.gauntletDropChance=.2;var personal=new PersonalBossSettings().snapshot(original);assertEquals(5500,personal.health,.001);assertEquals(.6375,personal.damageMitigation,.0001);assertEquals(10000,original.health);assertEquals(.85,original.damageMitigation);assertEquals(.15,personal.stoneDropChance);assertEquals(.2,personal.gauntletDropChance);assertTrue(personal.windupSeconds>original.windupSeconds);}
 @Test void invalidMultiplierIsRejected(){var cfg=new PersonalBossSettings();cfg.hp=Double.NaN;assertThrows(IllegalArgumentException.class,cfg::validate);}
}
