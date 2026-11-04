import com.quintonc.vs_sails.ship.HullStability;
import com.quintonc.vs_sails.ship.SeaMotion;
import com.quintonc.vs_sails.ship.WaterSupport;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class SeaMotionTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static double motion(int hz,double rain,double thunder){
        double dt=1.0/hz,total=0;int count=0;
        Quaterniond q=new Quaterniond();Vector3d omega=new Vector3d();
        for(int i=0;i<hz*60;i++){
            Vector3d acceleration=HullStability.acceleration(q,omega,.35,8,dt)
                    .add(SeaMotion.acceleration(q,.35,i*dt,0,0,16,rain,thunder,1));
            // Exercise both custom-water and helm damping along with the real trim controller.
            omega.fma(dt,acceleration.add(-6*omega.x,0,-6*omega.z));
            double speed=omega.length();
            if(speed>0)q.premul(new Quaterniond().rotationAxis(speed*dt,omega.x/speed,0,omega.z/speed)).normalize();
            double tilt=Math.acos(Math.max(-1,Math.min(1,q.transform(new Vector3d(0,1,0)).y)));
            check(omega.isFinite() && tilt<Math.toRadians(12),"no runaway roll or pitch");
            if(i>hz*15){total+=tilt*tilt;count++;}
        }
        return Math.sqrt(total/count);
    }
    public static void main(String[] args){
        for(int hz:new int[]{20,60,120}){
            double clear=motion(hz,0,0),rain=motion(hz,1,0),storm=motion(hz,1,1);
            check(clear>Math.toRadians(.1) && clear<Math.toRadians(2),"gentle clear-weather sway: "+clear);
            check(rain>clear*2 && storm>rain,"rain and thunder strengthen rocking");
            System.out.println("SEA_MOTION_TRACE hz="+hz+" clearDegrees="+Math.toDegrees(clear)+" rainDegrees="+Math.toDegrees(rain)+" thunderDegrees="+Math.toDegrees(storm));
        }
        Quaterniond q=new Quaterniond();
        check(SeaMotion.acceleration(q,0,1,0,0,16,1,1,1).lengthSquared()==0,"no waves in air");
        check(SeaMotion.acceleration(q,.3,1,0,0,16,1,1,0).lengthSquared()==0,"config disables waves");
        check(SeaMotion.acceleration(new Quaterniond().rotateX(2),.3,1,0,0,16,1,1,1).lengthSquared()==0,"no automatic capsize recovery");
        for(double bad:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY}){
            check(SeaMotion.acceleration(q,.3,bad,0,0,16,1,1,1).lengthSquared()==0,"non-finite wave time rejected");
            check(SeaMotion.acceleration(q,bad,1,0,0,16,1,1,1).lengthSquared()==0,"non-finite immersion rejected");
        }
        double last=0;
        for(int i=0;i<2000;i++){
            Vector3d a=SeaMotion.acceleration(q,.35,i*.01,0,0,16,i/2000.0,0,1);
            check(a.isFinite() && a.y==0 && a.length()<=1,"bounded horizontal torque with no yaw");
            if(i>0)check(Math.abs(a.x-last)<.02,"continuous wave response through weather changes");
            last=a.x;
        }
        double light=WaterSupport.force(1600,16*.35,16,3000,0,.05);
        check(Math.abs(light-16000)<1e-6,"visible draft is hydrostatic equilibrium");
        check(WaterSupport.force(16000,16*.35,16,1,0,.05)<160000,"cargo weight increases draft beyond the minimum");
        check(WaterSupport.force(1600,0,16,3000,0,.05)==0,"no airborne lift at the new draft");
        System.out.println("SEA_MOTION_PASS assertions="+checks);
    }
}
