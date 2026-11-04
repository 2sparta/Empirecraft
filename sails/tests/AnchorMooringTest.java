import com.quintonc.vs_sails.ship.AnchorTether;
import org.joml.Vector3d;

public final class AnchorMooringTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        Vector3d bottom=new Vector3d();
        check(AnchorTether.mooringForce(new Vector3d(.3,10.2,.2),bottom,new Vector3d(),10,1000,1.5).lengthSquared()==0,"slack chain permits small sway and heave");
        for(int hz:new int[]{20,60,120}){
            double dt=1.0/hz;Vector3d point=new Vector3d(0,10,0),velocity=new Vector3d(5,0,-2);
            for(int i=0;i<hz*50;i++){
                Vector3d f=AnchorTether.mooringForce(point,bottom,velocity,10,100000,1.5);
                check(f.isFinite()&&f.length()<800001,"bounded finite mooring force");
                velocity.fma(dt/100000,f.add(Math.sin(i*dt)*3000,0,Math.cos(i*dt)*3000));
                velocity.y=0;point.fma(dt,velocity);point.y=10;
                if(i>hz*40)check(Math.hypot(point.x,point.z)<1.8,"bounded berth after deployment braking");
            }
            check(velocity.lengthSquared()>1e-7,"anchor does not freeze motion");
        }
        check(AnchorTether.mooringForce(new Vector3d(Double.NaN,0,0),bottom,new Vector3d(),10,1000,1.5).lengthSquared()==0,"invalid anchor state is safe");
        System.out.println("ANCHOR_MOORING_PASS assertions="+checks);
    }
}
