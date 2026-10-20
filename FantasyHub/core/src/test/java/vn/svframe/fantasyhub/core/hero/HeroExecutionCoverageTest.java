package vn.svframe.fantasyhub.core.hero;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
/** Registry coverage complements actual world-fixture tests; it does not certify damage/VFX. */
class HeroExecutionCoverageTest {
  @Test void everyConfiguredActiveHasAnExecutableNativeHandler() throws Exception {
    var c=HeroCatalog.defaults();
    @SuppressWarnings("unchecked")
    var ids=(Set<String>)Class.forName("vn.svframe.fantasyhub.core.hero.HeroSkillRouter").getMethod("abilities").invoke(null);
    assertEquals(c.abilities().keySet(),ids);
  }
}
