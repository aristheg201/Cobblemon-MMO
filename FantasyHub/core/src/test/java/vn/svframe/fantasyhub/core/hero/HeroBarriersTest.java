package vn.svframe.fantasyhub.core.hero;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
class HeroBarriersTest {
  @AfterEach void clear(){HeroBarriers.clear();}
  @Test void recastRefreshesInsteadOfAddingASecondShield() {
    var owner=UUID.randomUUID();var target=UUID.randomUUID();
    HeroBarriers.add(owner,target,"dash",100,.15,100);assertEquals(10,HeroBarriers.absorb(target,25));
    HeroBarriers.add(owner,target,"dash",100,.15,100);HeroBarriers.add(owner,target,"dash",100,.15,120);
    assertEquals(15,HeroBarriers.amount(target));
  }
  @Test void independentShieldsAreBoundedAndExpireIndependently() {
    var one=UUID.randomUUID();var two=UUID.randomUUID();var target=UUID.randomUUID();
    HeroBarriers.add(one,target,"cloak",100,.2,50);HeroBarriers.add(two,target,"bifrost",100,.2,100);
    assertEquals(35,HeroBarriers.amount(target));HeroBarriers.tick(50);assertEquals(15,HeroBarriers.amount(target));
    HeroBarriers.release(two);assertEquals(0,HeroBarriers.amount(target));
  }
  @Test void destroyedShieldCannotReturnFromAnOldExpirationCallback() {
    var owner=UUID.randomUUID();var target=UUID.randomUUID();
    HeroBarriers.add(owner,target,"cloak",100,.2,100);HeroBarriers.destroy(target);HeroBarriers.tick(100);
    assertEquals(8,HeroBarriers.absorb(target,8));
  }
}
