package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.List;

/** Explicit cancellation stops queued movement, manoeuvres and every unissued shot. */
public final class TacticalOrders {
    private TacticalOrders() {}
    public static void replaceMovement(AbstractRecruitEntity r) {
        TacticalRoutes.forget(r); TacticalMovement.cancel(r); TacticalCombat.cancel(r);
    }
    public static void interruptMovement(AbstractRecruitEntity r) {
        TacticalRoutes.forget(r); if (TacticalMovement.moving(r)) TacticalMovement.cancel(r); TacticalCombat.stopManoeuvre(r);
    }
    public static void nativeMovement(List<AbstractRecruitEntity> recruits) { recruits.forEach(TacticalOrders::replaceMovement); }
    public static void cancel(AbstractRecruitEntity r) {
        replaceMovement(r); TacticalFire.hold(r, true); r.m_5810_(); r.setShouldBlock(false);
        r.m_6710_(null); r.setFollowState(3); r.setHoldPos(TacticalActor.position(r)); TacticalActor.stop(r);
    }
    public static void leave(AbstractRecruitEntity r) { replaceMovement(r); TacticalFire.forget(r); }
}
