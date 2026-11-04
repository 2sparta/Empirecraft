import com.quintonc.vs_sails.ship.ArcadeSailing;
import com.quintonc.vs_sails.ship.DragCompensator;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class ArcadeSailingTest {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void near(double a, double b, String message) { check(Math.abs(a-b) < 1e-8, message + " " + a + "/" + b); }
    public static void main(String[] args) {
        for (double[] axis : new double[][]{{1,0},{0,1},{-1,0},{0,-1}}) {
            for (int yaw=0; yaw<360; yaw+=15) {
                Quaterniond rotation = new Quaterniond().rotateY(Math.toRadians(yaw));
                Vector3d bow = ArcadeSailing.heading(rotation, axis[0], axis[1]);
                near(bow.length(),1,"unit heading");
                for (int wind=0; wind<360; wind+=15) {
                    double target = ArcadeSailing.targetSpeed(bow,wind,.8,8,25000,10000,true);
                    check(target >= 0 && target <= 18,"nonnegative bounded speed at every wind angle");
                    near(target,ArcadeSailing.targetSpeed(bow,wind+180,-.8,8,25000,10000,true),"signed wind uses the same vector as indicators");
                    Vector3d rotatedBow = new Quaterniond().rotateY(.4).transform(bow,new Vector3d());
                    near(target,ArcadeSailing.targetSpeed(rotatedBow,wind-Math.toDegrees(.4),.8,8,25000,10000,true),"coordinate rotation equivariance");
                }
            }
        }
        Vector3d bow = new Vector3d(1,0,0);
        double tail = ArcadeSailing.targetSpeed(bow,0,1,8,25000,10000,true);
        double head = ArcadeSailing.targetSpeed(bow,180,1,8,25000,10000,true);
        near(tail,18,"tailwind maximum");near(head,0,"headwind has no forward drive");
        near(ArcadeSailing.targetSpeed(bow,90,1,8,25000,10000,true),13.59,"beam wind has useful forward speed");
        near(ArcadeSailing.targetSpeed(bow,0,.25,8,25000,10000,true),9,"light wind is not squared into a crawl");
        near(ArcadeSailing.targetSpeed(bow,0,1,1,25000,100000,true),2.25,"sail to mass capacity is requires a useful area of deployed cloth");
        double previous = 0;
        for(int sails=1;sails<=256;sails++) {
            double speed=ArcadeSailing.targetSpeed(bow,0,.5,sails,25000,2e6,true);
            check(speed>=previous && speed<=18,"each additional sail helps until the speed cap");previous=speed;
        }
        check(previous>5,"heavy ship with limited cloth retains useful cruising speed");
        Vector3d wetVelocity=new Vector3d();
        DragCompensator drag=new DragCompensator();
        for(int tick=0;tick<3600;tick++) {
            drag.observe(wetVelocity);
            Vector3d thrust=ArcadeSailing.acceleration(bow,wetVelocity,tail,true);
            check(thrust.x<=40 && thrust.x>=-12,"forward thrust stays bounded");
            thrust.x+=drag.compensation(wetVelocity.x);
            drag.record(bow,wetVelocity,thrust.x,1.0/60);
            thrust.x-=.25*wetVelocity.x*Math.abs(wetVelocity.x); // Representative quadratic hull drag, not the VS solver.
            wetVelocity.add(thrust.mul(1.0/60));
        }
        check(wetVelocity.x>10 && wetVelocity.x<18,"available thrust overcomes substantial water resistance");
        near(ArcadeSailing.acceleration(bow,new Vector3d(),tail,true).x,.8,"launch still starts gently");
        check(ArcadeSailing.acceleration(bow,new Vector3d(20,0,0),tail,true).x<0,"overspeed brakes rather than adding drag compensation");
        near(ArcadeSailing.targetSpeed(bow,90,0,8,25000,10000,true),0,"calm has no artificial motor");
        near(ArcadeSailing.targetSpeed(bow,0,1,0,25000,10000,true),0,"doused sails have no drive");
        check(ArcadeSailing.targetSpeed(bow,0,1,1,25000,1e8,true)<tail,"underpowered heavy ship remains slower");
        Vector3d velocity = new Vector3d(0,0,4);
        for (int i=0;i<6000;i++) velocity.add(ArcadeSailing.acceleration(bow,velocity,tail,true).mul(1.0/60));
        check(velocity.x>17.99 && Math.abs(velocity.z)<.001,"lateral drift dies out and boat follows bow");
        Vector3d headVelocity = new Vector3d();
        for (int i=0;i<6000;i++) headVelocity.add(ArcadeSailing.acceleration(bow,headVelocity,head,true).mul(1.0/60));
        near(headVelocity.x,0,"headwind cannot launch a stationary ship");
        Vector3d stopped = new Vector3d(8,.1,-3), omega = new Vector3d(.2,.4,-.1);
        int heldAt=-1;
        for (int i=0;i<600;i++) {
            Vector3d brake=ArcadeSailing.anchorBrake(stopped);
            check(brake.dot(stopped)<=1e-9 && brake.length()<=10.000001,"anchor brakes with bounded acceleration");
            stopped.add(brake.mul(1.0/60));
            omega.add(ArcadeSailing.angularAcceleration(omega,1,8,7,5,true).mul(1.0/60));
            if(heldAt<0 && ArcadeSailing.canHold(stopped,omega)) heldAt=i;
        }
        check(heldAt<0,"gentle mooring damping does not snap roll and pitch into the former full-hold threshold");
        check(ArcadeSailing.angularAcceleration(new Vector3d(),1,3,7,5,false).y>0,"left wheel turns left around world up");
        check(ArcadeSailing.angularAcceleration(new Vector3d(),-1,3,7,5,false).y<0,"right wheel turns right");
        check(ArcadeSailing.angularAcceleration(new Vector3d(0,.3,0),0,3,7,5,false).y<0,"manually centred wheel damps yaw");
        near(ArcadeSailing.turnRateDegrees(7,5),32,"small ship turn rate");
        check(ArcadeSailing.turnRateDegrees(30,12)<32,"medium hull turns more slowly");
        check(ArcadeSailing.turnRateDegrees(100,30)<ArcadeSailing.turnRateDegrees(30,12),"large hull turns more slowly");
        near(ArcadeSailing.turnRateDegrees(500,150),10,"huge hull retains the minimum turn rate");
        for(int size=1;size<=1000;size++) {
            double rate=ArcadeSailing.turnRateDegrees(size,size/3.0);
            check(rate>=10 && rate<=32,"turning speed remains bounded for all hull sizes");
            near(rate,ArcadeSailing.turnRateDegrees(size/3.0,size),"hull orientation does not change turn speed");
        }
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            near(ArcadeSailing.targetSpeed(bow,bad,1,8,25000,10000,true),0,"invalid direction rejected");
            check(ArcadeSailing.anchorBrake(new Vector3d(bad,0,0)).lengthSquared()==0,"invalid brake input rejected");
        }
        System.out.println("PASS: "+assertions+" arcade sailing assertions");
    }
}

