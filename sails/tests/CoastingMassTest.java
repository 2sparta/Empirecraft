import com.quintonc.vs_sails.ship.ArcadeSailing;
import org.joml.Vector3d;

public final class CoastingMassTest {
    private static int assertions;
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void near(double actual, double expected, String message) { check(Math.abs(actual-expected)<1e-8,message+": "+actual+" / "+expected); }
    private static double speed(int open, int furled, double mass) {
        return ArcadeSailing.targetSpeed(new Vector3d(1,0,0),0,1,open,furled,25000,mass,true);
    }
    public static void main(String[] args) {
        near(speed(0,6,20000),2,"well rigged furled ship keeps a slow wind-driven target");
        near(speed(0,6,600000),1,"fourfold underpowered mass halves reduced-rig speed");
        near(speed(0,24,600000),2,"four times the furled cloth supports four times the mass");
        near(speed(0,0,20000),0,"no cloth has no residual sail drive");
        near(ArcadeSailing.targetSpeed(new Vector3d(1,0,0),0,0,0,6,25000,20000,true),0,"true calm has no motor");
        near(ArcadeSailing.targetSpeed(new Vector3d(1,0,0),180,1,0,6,25000,20000,true),.04,"headwind remains very slow even when furled");
        near(speed(2,6,1e6),speed(2,0,1e6),"furled blocks are not counted as fully deployed cloth");
        for(int cloth=1;cloth<=100;cloth++) {
            double mass=cloth*100000.0;
            near(speed(cloth,0,mass*4),speed(cloth,0,mass)/2,"open sails: quadruple mass halves underpowered speed");
            near(speed(cloth*4,0,mass*4),speed(cloth,0,mass),"open sails: same area/mass preserves speed");
            near(speed(0,cloth,mass*4),speed(0,cloth,mass)/2,"furled sails: mass still matters");
            check(speed(cloth+1,0,mass)>speed(cloth,0,mass),"adding cloth increases underpowered speed");
        }
        Vector3d bow=new Vector3d(1,0,0), velocity=new Vector3d(14,0,0);
        for(int tick=0;tick<60;tick++) velocity.add(ArcadeSailing.coastingAcceleration(bow,velocity,2,20000,true).mul(1.0/60));
        check(velocity.x>13.1 && velocity.x<14,"furling does not slam the brakes in the first second");
        for(int tick=0;tick<3540;tick++) velocity.add(ArcadeSailing.coastingAcceleration(bow,velocity,2,20000,true).mul(1.0/60));
        check(velocity.x>1.99 && velocity.x<2.01,"furled ship settles to slow movement instead of full stop");
        double light=ArcadeSailing.coastingAcceleration(bow,new Vector3d(14,0,0),2,20000,true).x;
        double heavy=ArcadeSailing.coastingAcceleration(bow,new Vector3d(14,0,0),2,80000,true).x;
        check(heavy<0 && Math.abs(heavy)<Math.abs(light),"heavy hull retains more momentum after furling");
        Vector3d slow=new Vector3d();
        for(int tick=0;tick<1800;tick++) slow.add(ArcadeSailing.coastingAcceleration(bow,slow,2,20000,true).mul(1.0/60));
        check(slow.x>1.99 && slow.x<=2,"furled windage can maintain slow headway from rest");
        near(ArcadeSailing.coastingAcceleration(bow,new Vector3d(4,2,0),2,20000,true).y,0,"no vertical damper while falling in air");
        near(ArcadeSailing.coastingAcceleration(bow,new Vector3d(4,2,0),2,20000,true).y,0,"native vertical water damping remains in charge");
        check(ArcadeSailing.coastingAcceleration(bow,new Vector3d(8,0,3),2,20000,true).z<0,"arcade lateral grip remains active");
        check(ArcadeSailing.reverseEngaged(false,true,new Vector3d(1.49,4,0)),"reverse starts below horizontal threshold despite buoyancy bobbing");
        check(!ArcadeSailing.reverseEngaged(false,true,new Vector3d(1.5,0,0)),"reverse does not start at threshold");
        check(!ArcadeSailing.reverseEngaged(false,true,new Vector3d(1.4,0,.6)),"sideways speed counts toward engagement threshold");
        check(!ArcadeSailing.reverseEngaged(false,true,new Vector3d(8,0,0)),"back key is not a high-speed brake");
        check(!ArcadeSailing.reverseEngaged(true,false,new Vector3d(-1.5,0,0)),"release or lost pilot disengages reverse");
        check(ArcadeSailing.reverseEngaged(true,true,new Vector3d(-1.51,0,0)),"engaged reverse does not chatter at its speed limit");
        for(double mass:new double[]{10000,20000,80000,1e6}) {
            Vector3d reverse=new Vector3d(1.4,0,0);
            for(int tick=0;tick<3600;tick++) {
                reverse.add(ArcadeSailing.reverseAcceleration(bow,reverse,mass,true).mul(1.0/60));
                check(reverse.x>=-1.500001,"reverse never accelerates past the speed limit");
            }
            check(reverse.x<-1.49,"reverse settles to slow sternward movement for all masses");
        }
        System.out.println("PASS: "+assertions+" coasting and sail-to-mass assertions");
    }
}
