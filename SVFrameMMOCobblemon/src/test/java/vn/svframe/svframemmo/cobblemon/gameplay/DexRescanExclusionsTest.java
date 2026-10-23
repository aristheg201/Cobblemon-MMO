package vn.svframe.svframemmo.cobblemon.gameplay;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import java.nio.file.Path;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class DexRescanExclusionsTest {
 @TempDir Path root;
 @Test void erasedOwnedRecordsStayErasedAcrossReloadButNewCapturesRemainEligible()throws Exception{
  var file=root.resolve("exclusions.json");var player=UUID.randomUUID();var old=UUID.randomUUID();var fresh=UUID.randomUUID();var other=UUID.randomUUID();
  var policy=new DexRescanExclusions(file);policy.exclude(player,List.of(old));
  var reloaded=new DexRescanExclusions(file);assertFalse(reloaded.keeps(player,old));assertTrue(reloaded.keeps(player,fresh));assertTrue(reloaded.keeps(other,old));
  reloaded.exclude(player,List.of(fresh));var again=new DexRescanExclusions(file);assertFalse(again.keeps(player,old));assertFalse(again.keeps(player,fresh));
 }
}
