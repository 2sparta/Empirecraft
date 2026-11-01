import com.quintonc.vs_sails.ship.ShipMotionSafety;
import org.joml.Vector3d;

public final class ShipMotionSafetyTest {
    static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args) {
        check(ShipMotionSafety.needsHull(1,0),"single cloth");check(ShipMotionSafety.needsHull(10000,0),"all cloth");
        check(!ShipMotionSafety.needsHull(100,1),"real hull or buoy allows sailing");
        check(!ShipMotionSafety.needsHull(0,0),"empty attachment");
        check(!ShipMotionSafety.unsafe(new Vector3d(10,100,-20),new Vector3d(18,0,0),new Vector3d(0,.5,0)),"normal sailing");
        check(ShipMotionSafety.unsafe(new Vector3d(),new Vector3d(1e7,0,0),new Vector3d()),"runaway speed");
        check(ShipMotionSafety.unsafe(new Vector3d(),new Vector3d(),new Vector3d(0,13,0)),"runaway rotation");
        for(double n:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            check(ShipMotionSafety.unsafe(new Vector3d(n,0,0),new Vector3d(),new Vector3d()),"invalid position");
            check(ShipMotionSafety.unsafe(new Vector3d(),new Vector3d(0,n,0),new Vector3d()),"invalid velocity");
            check(ShipMotionSafety.unsafe(new Vector3d(),new Vector3d(),new Vector3d(0,0,n)),"invalid rotation");
        }
        check(ShipMotionSafety.unsafe(new Vector3d(30000000,0,0),new Vector3d(),new Vector3d()),"world border");
        System.out.println("SHIP_MOTION_SAFETY_PASS assertions="+checks);
    }
}
