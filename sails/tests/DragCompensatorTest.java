import com.quintonc.vs_sails.ship.ArcadeSailing;
import com.quintonc.vs_sails.ship.DragCompensator;
import org.joml.Vector3d;

public final class DragCompensatorTest {
    private static int assertions;
    private static void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
    private static void step(DragCompensator predictor,Vector3d velocity,double resistance,double dt,boolean slow,boolean reverse) {
        var bow=new Vector3d(1,0,0);predictor.observe(velocity);
        double force=(reverse?ArcadeSailing.reverseAcceleration(bow,velocity,20000,true)
                :slow?ArcadeSailing.coastingAcceleration(bow,velocity,2,20000,true)
                :ArcadeSailing.acceleration(bow,velocity,18,true)).x;
        if(slow)force+=predictor.compensation(velocity.x);
        predictor.record(bow,velocity,force,dt);
        double drag=resistance*velocity.x*Math.abs(velocity.x);
        velocity.x+=(force-drag)*dt;
        check(Double.isFinite(velocity.x)&&Math.abs(force)<=101.3,"bounded finite adaptive assistance");
    }
    public static void main(String[] args) {
        for(int hz:new int[]{20,40,60,120})for(double drag:new double[]{.1,.25,.5}) {
            var predictor=new DragCompensator();var velocity=new Vector3d();double dt=1.0/hz;
            for(int tick=0;tick<hz*12;tick++)step(predictor,velocity,drag,dt,false,false);
            double before=velocity.x;
            for(int tick=0;tick<hz;tick++)step(predictor,velocity,drag,dt,true,false);
            check(velocity.x>before-1.3,"learned drag preserves momentum after furling at different physics rates");
            for(int tick=0;tick<hz*55;tick++)step(predictor,velocity,drag,dt,true,false);
            check(velocity.x>1.97&&velocity.x<2.03,"adaptive assistance maintains two blocks/s against water drag");
            predictor.reset();velocity.zero();
            for(int tick=0;tick<hz*15;tick++)step(predictor,velocity,drag,dt,true,true);
            check(velocity.x>-1.55&&velocity.x<-1.45,"reverse overcomes low-speed water resistance");
            predictor.reset();
            check(predictor.compensation(14)==0,"reset discards old resistance after a fresh motion sequence");
        }
        System.out.println("PASS: "+assertions+" adaptive drag assertions");
    }
}
