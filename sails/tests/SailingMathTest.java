import com.quintonc.vs_sails.ship.SailingMath;
import com.quintonc.vs_sails.ship.AnchorTether;
import org.joml.Vector3d;

public class SailingMathTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void near(double a, double b, String message) { check(Math.abs(a-b)<1e-8, message); }
    public static void main(String[] args) {
        double forward = SailingMath.rudderForce(.2, 3, 998, 10000, .5, true);
        double reverse = SailingMath.rudderForce(.2, -3, 998, 10000, .5, true);
        check(forward > 0, "forward steering");
        near(forward, -reverse, "reverse must invert steering");
        near(SailingMath.rudderForce(0, 3, 998, 10000, .5, true), 0, "centred rudder has no torque");
        near(SailingMath.rudderForce(.2, 0, 998, 10000, .5, true), 0, "stationary realistic rudder");
        near(SailingMath.rudderForce(Double.NaN, 3, 998, 10000, .5, true), 0, "reject NaN");
        near(SailingMath.rudderForce(.2, Double.POSITIVE_INFINITY, 998, 10000, .5, true), 0, "reject infinite speed");
        near(SailingMath.angleDistance(Math.toRadians(359), Math.toRadians(1)), Math.toRadians(2), "wind crosses zero");
        near(SailingMath.angleDistance(-Math.PI, Math.PI), 0, "equivalent angles");
        for (int degree=-720;degree<=720;degree++) {
            double angle=SailingMath.angleDistance(Math.toRadians(degree), .42);
            check(angle>=0 && angle<=Math.PI, "wind angle bounded");
            near(angle, SailingMath.angleDistance(Math.toRadians(degree+360), .42), "wind periodic");
        }
        var point=new Vector3d(); var bottom=new Vector3d(10,0,0);
        near(AnchorTether.force(point,bottom,new Vector3d(),12,1000).length(),0,"slack chain does not push");
        Vector3d force=AnchorTether.force(point,bottom,new Vector3d(),5,1000);
        check(force.x>0 && force.y==0 && force.z==0,"chain pulls towards anchor");
        check(force.length()<=4000,"chain acceleration is bounded");
        check(AnchorTether.force(point,bottom,new Vector3d(-3,0,0),5,1000).x>=AnchorTether.force(point,bottom,new Vector3d(3,0,0),5,1000).x,"chain damping resists outward movement");
        near(AnchorTether.force(point,bottom,new Vector3d(),5,Double.NaN).length(),0,"anchor rejects invalid mass");
        System.out.println("PASS: "+assertions+" sailing and anchor assertions");
    }
}
