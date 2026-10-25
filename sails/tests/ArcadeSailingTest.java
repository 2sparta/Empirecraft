import com.quintonc.vs_sails.ship.ArcadeSailing;
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
                    check(target > 0 && target <= 6,"positive bounded speed at every wind angle");
                    near(target,ArcadeSailing.targetSpeed(bow,wind+180,-.8,8,25000,10000,true),"signed wind uses the same vector as indicators");
                    Vector3d rotatedBow = new Quaterniond().rotateY(.4).transform(bow,new Vector3d());
                    near(target,ArcadeSailing.targetSpeed(rotatedBow,wind-Math.toDegrees(.4),.8,8,25000,10000,true),"coordinate rotation equivariance");
                }
            }
        }
        Vector3d bow = new Vector3d(1,0,0);
        double tail = ArcadeSailing.targetSpeed(bow,0,1,8,25000,10000,true);
        double head = ArcadeSailing.targetSpeed(bow,180,1,8,25000,10000,true);
        near(tail,6,"tailwind maximum");near(head,.12,"headwind crawls at two percent");
        near(ArcadeSailing.targetSpeed(bow,90,0,8,25000,10000,true),0,"calm has no artificial motor");
        near(ArcadeSailing.targetSpeed(bow,0,1,0,25000,10000,true),0,"doused sails have no drive");
        check(ArcadeSailing.targetSpeed(bow,0,1,1,25000,1e8,true)<tail,"underpowered heavy ship remains slower");
        Vector3d velocity = new Vector3d(0,0,4);
        for (int i=0;i<600;i++) velocity.add(ArcadeSailing.acceleration(bow,velocity,tail,true).mul(1.0/60));
        check(velocity.x>5.99 && Math.abs(velocity.z)<.001,"lateral drift dies out and boat follows bow");
        Vector3d headVelocity = new Vector3d();
        for (int i=0;i<600;i++) headVelocity.add(ArcadeSailing.acceleration(bow,headVelocity,head,true).mul(1.0/60));
        check(headVelocity.x>.119 && headVelocity.x<.121,"headwind converges to a crawl, not reverse");
        Vector3d stopped = new Vector3d(8,.1,-3), omega = new Vector3d(.2,.4,-.1);
        int heldAt=-1;
        for (int i=0;i<600;i++) {
            Vector3d brake=ArcadeSailing.anchorBrake(stopped);
            check(brake.dot(stopped)<=1e-9 && brake.length()<=10.000001,"anchor brakes with bounded acceleration");
            stopped.add(brake.mul(1.0/60));
            omega.add(ArcadeSailing.angularAcceleration(omega,1,8,true).mul(1.0/60));
            if(heldAt<0 && ArcadeSailing.canHold(stopped,omega)) heldAt=i;
        }
        check(heldAt>0 && heldAt<240,"anchor reaches hold threshold within four seconds");
        check(ArcadeSailing.angularAcceleration(new Vector3d(),1,3,false).y>0,"left wheel turns left around world up");
        check(ArcadeSailing.angularAcceleration(new Vector3d(),-1,3,false).y<0,"right wheel turns right");
        check(ArcadeSailing.angularAcceleration(new Vector3d(0,.3,0),0,3,false).y<0,"centred wheel damps yaw");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            near(ArcadeSailing.targetSpeed(bow,bad,1,8,25000,10000,true),0,"invalid direction rejected");
            check(ArcadeSailing.anchorBrake(new Vector3d(bad,0,0)).lengthSquared()==0,"invalid brake input rejected");
        }
        System.out.println("PASS: "+assertions+" arcade sailing assertions");
    }
}
