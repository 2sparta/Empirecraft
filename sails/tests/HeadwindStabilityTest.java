import com.quintonc.vs_sails.ship.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class HeadwindStabilityTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void wind(){
        Vector3d bow=new Vector3d(1,0,0);
        for(int angle=0;angle<360;angle++){
            double factor=ArcadeSailing.windFactor(bow,angle,1);
            boolean forbidden=angle>=135 && angle<=225;
            if(forbidden)check(factor==0,"no propulsion throughout upwind sector: "+angle);
            check(factor>=0 && factor<=1,"bounded wind efficiency");
            check(Math.abs(factor-ArcadeSailing.windFactor(bow,angle+180,-1))<1e-12,"signed wind vector");
        }
        check(ArcadeSailing.windFactor(bow,120,1,120)==0,"configured wider no-sail sector");
        check(ArcadeSailing.windFactor(bow,120,1,60)>0,"configured narrow sector permits reaching");
        double last=0;
        for(double angle=135;angle>=100;angle-=.01){
            double next=ArcadeSailing.windFactor(bow,angle,1);
            check(next>=last-1e-10 && next-last<.002,"smooth exit from no-sail sector");last=next;
        }
        for(int hz:new int[]{20,60,120})for(double mass:new double[]{20000,1e6,1e8})for(boolean furled:new boolean[]{false,true})for(double drag:new double[]{0,.185}){
            SailDrive drive=new SailDrive();Vector3d v=new Vector3d();double dt=1.0/hz;
            // Train the actual drag observer under load before changing wind.
            for(int i=0;i<hz*90;i++){
                Vector3d a=drive.acceleration(bow,v,18,mass,true,false,false,false,dt);
                v.x+=(a.x-drag*v.x*Math.abs(v.x))*dt;
            }
            v.set(18,0,0);double stopTime=-1;
            for(int i=0;i<hz*18;i++){
                Vector3d a=drive.acceleration(bow,v,0,mass,true,false,false,furled,dt);
                check(a.x<=0 && drive.assistance()==0,"headwind always removes forward momentum");
                v.x+=(a.x-drag*v.x*Math.abs(v.x))*dt;
                check(v.x>=-1e-8 && v.isFinite(),"stable stopping without reversal");
                if(stopTime<0 && v.x<.12)stopTime=i*dt;
            }
            check(stopTime>0 && stopTime<12,"heavy and furled ships stop within twelve seconds: "+mass+"/"+stopTime);
            for(int i=0;i<hz*3;i++)v.fma(dt,drive.acceleration(bow,v,0,mass,true,false,false,furled,dt));
            check(v.x<.001,"no sustained powered crawl");
            Vector3d reverse=new Vector3d(-1,0,0);
            check(drive.acceleration(bow,reverse,-1.5,mass,true,false,true,false,dt).x<0,"pilot reverse remains available");
        }
    }
    static void stability(){
        for(int hz:new int[]{20,60,120})for(double yaw:new double[]{0,.7,2.1}){
            double dt=1.0/hz;Quaterniond rotation=new Quaterniond().rotateY(yaw).rotateX(.35).rotateZ(-.15);
            Vector3d omega=new Vector3d();double peak=0;
            for(int i=0;i<hz*30;i++){
                Vector3d a=HullStability.acceleration(rotation,omega,.35,8,dt);
                check(a.isFinite() && a.y==0 && a.length()<=1.5000001,"bounded roll/pitch help with no yaw command");
                // Persistent unbalanced loading, plus ordinary rotational water resistance.
                omega.fma(dt,a.add(.35,0,0)).mul(Math.exp(-2*dt));
                double turn=omega.length()*dt;
                if(turn>0)rotation.premul(new Quaterniond().rotationAxis(turn,omega.x/omega.length(),omega.y/omega.length(),omega.z/omega.length())).normalize();
                Vector3d up=rotation.transform(new Vector3d(0,1,0));double tilt=Math.acos(Math.max(-1,Math.min(1,up.y)));
                peak=Math.max(peak,tilt);
                if(i==hz*29)check(tilt<Math.toRadians(8),"stationary uneven loading settles under eight degrees: "+tilt);
            }
            check(peak<Math.toRadians(26),"no launch-like angular impulse");
        }
        for(double pitch:new double[]{-.7,-.2,.2,.7})for(double roll:new double[]{-.7,.7}){
            Quaterniond q=new Quaterniond().rotateX(pitch).rotateZ(roll);
            Vector3d up=q.transform(new Vector3d(0,1,0));Vector3d restore=up.cross(new Vector3d(0,1,0));
            check(HullStability.acceleration(q,new Vector3d(),.4,8,.05).dot(restore)>0,"both axes restore toward level");
        }
        check(HullStability.acceleration(new Quaterniond().rotateX(.4),new Vector3d(),0,8,.05).lengthSquared()==0,"no righting in air");
        check(HullStability.acceleration(new Quaterniond().rotateX(.4),new Vector3d(),1,0,.05).lengthSquared()==0,"config disables assistance");
        check(HullStability.acceleration(new Quaterniond().rotateX(2),new Vector3d(),1,8,.05).lengthSquared()==0,"capsized hull not automatically flipped");
        check(HullStability.acceleration(new Quaterniond(),new Vector3d(0,.7,0),1,8,.05).lengthSquared()==0,"turning preserved");
        check(HullStability.acceleration(new Quaterniond(),new Vector3d(),Double.NaN,8,.05).lengthSquared()==0,"invalid fluid frame safe");
    }
    public static void main(String[] args){wind();stability();System.out.println("HEADWIND_STABILITY_PASS assertions="+checks);}
}
