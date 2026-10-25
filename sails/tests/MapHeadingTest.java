import com.quintonc.vs_sails.client.MapHeading;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class MapHeadingTest {
    private static int assertions;
    private static void nearAngle(double actual,double expected,String message) {
        assertions++; double delta=(actual-expected)%360; if(delta>180)delta-=360;if(delta<-180)delta+=360;
        if(!Double.isFinite(actual)||Math.abs(delta)>1e-7)throw new AssertionError(message+": "+actual+" / "+expected);
    }
    public static void main(String[] args) {
        nearAngle(MapHeading.worldYaw(new Quaterniond(),90,0),90,"identity preserves west");
        var halfTurn=new Quaterniond().rotateY(Math.PI);
        nearAngle(MapHeading.worldYaw(halfTurn,90,0),-90,"west-facing seated player turns east with the ship");
        nearAngle(MapHeading.worldYaw(halfTurn,120,0),-60,"turning the head is still respected");
        for(int shipYaw=0;shipYaw<360;shipYaw+=15)for(int roll:new int[]{-25,0,25})for(int pitch:new int[]{-60,-20,0,20,60})for(int head=-180;head<180;head+=15) {
            Quaterniond ship=new Quaterniond().rotateY(Math.toRadians(shipYaw)).rotateZ(Math.toRadians(roll));
            // Independent oracle: the exact quaternion composition used by VS's camera setup.
            Quaterniond camera=new Quaterniond(ship).mul(new Quaterniond().rotateY(Math.toRadians(-head)).rotateX(Math.toRadians(pitch)));
            Vector3d forward=camera.transform(new Vector3d(0,0,1));
            double expected=Math.toDegrees(Math.atan2(-forward.x,forward.z));
            nearAngle(MapHeading.worldYaw(ship,head,pitch),expected,"mounted map heading agrees with VS camera quaternion");
        }
        nearAngle(MapHeading.worldYaw(new Quaterniond().rotateZ(Math.toRadians(25)),90,65),90,"vertical world look keeps a stable horizontal fallback");
        nearAngle(MapHeading.worldYaw(null,45,0),45,"missing ship leaves ordinary player heading unchanged");
        System.out.println("PASS: "+assertions+" map heading assertions");
    }
}
