package vn.svframe.fantasyhub.core.rider;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RiderStateTest {
    @Test void marksRefreshExpireCapAndBelongToTheirCaster(){
        UUID target=UUID.randomUUID();var a=new ZoldaState();var b=new ZoldaState();
        assertEquals(1,a.mark(target,0,100));assertEquals(2,a.mark(target,20,100));
        assertEquals(3,a.mark(target,40,100));assertEquals(3,a.mark(target,60,100));
        assertEquals(0,b.stacks(target,60));assertEquals(3,a.stacks(target,159));assertEquals(0,a.stacks(target,160));
        assertEquals(1,a.mark(target,160,100));
    }
    @Test void strikeShieldAbsorbsFifteenPercentAndCannotRecastTwice(){
        var s=new ZoldaState();s.shield(200,.15,10,80);
        assertEquals(30,s.shield(10));assertEquals(0,s.absorb(20,11));assertEquals(10,s.absorb(20,12));
        assertTrue(s.release(13));assertFalse(s.release(13));assertEquals(0,s.shield(13));
        s.shield(200,.15,100,80);assertEquals(0,s.shield(180));assertFalse(s.release(180));
    }
    @Test void finalVentIsExactlyAForwardOneHundredTwentyDegreeCone(){
        assertTrue(ZoldaState.inCone(0,1,0,32,32,120));
        assertTrue(ZoldaState.inCone(0,1,Math.sqrt(3),1,32,120));
        assertFalse(ZoldaState.inCone(0,1,2,0,32,120));
        assertFalse(ZoldaState.inCone(0,1,0,-1,32,120));
        assertFalse(ZoldaState.inCone(0,1,0,33,32,120));
    }
    @Test void forceCannotAccumulateUntransformedAndOnlyEmpowersOnce(){
        var s=new AgitoState();assertFalse(s.gain());assertEquals(0,s.force());s.henshin();
        for(int i=0;i<5;i++)assertFalse(s.gain());assertTrue(s.gain());assertFalse(s.gain());
        assertEquals(6,s.force());assertTrue(s.consume());assertFalse(s.consume());assertEquals(0,s.force());
    }
    @Test void temporaryFormsKeepTheirDurationWhenBaseSkillsChangeStance(){
        var s=new AgitoState();s.henshin();s.elevate(AgitoState.Form.TRINITY,10,200);s.stance(AgitoState.Form.FLAME);
        assertEquals(AgitoState.Form.TRINITY,s.form(209));assertEquals(AgitoState.Form.FLAME,s.form(210));
        s.elevate(AgitoState.Form.SHINING,220,240);assertEquals(AgitoState.Form.SHINING,s.form(459));
        assertEquals(AgitoState.Form.FLAME,s.form(460));s.henshin();assertFalse(s.transformed());assertEquals(0,s.remaining(461));
    }
}
