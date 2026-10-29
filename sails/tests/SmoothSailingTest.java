import com.quintonc.vs_sails.ship.*;
import com.quintonc.vs_sails.wind.SmoothWind;
import org.joml.Vector3d;

public final class SmoothSailingTest {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static void near(double actual,double expected,String name){check(Math.abs(actual-expected)<1e-8,name+": "+actual+"/"+expected);}
    public static void main(String[] args) {
        SailExposure rig=new SailExposure();rig.addSail(0,1,5,true);rig.addSail(1,1,5,true);rig.addSail(7,1,5,true);
        check(rig.count().open()==1,"three nearby layers supply one projected cloth cell");
        rig.addSail(15,1,5,true);check(rig.count().open()==2,"exactly eight blocks is a separate sail");
        rig.addSail(15,2,5,true);rig.addSail(15,1,6,true);check(rig.count().open()==4,"different lateral cells and heights retain their area");
        rig.addWood(1,5);check(rig.count().open()==2,"wood on the same ship ray shelters both forward and aft rigs");
        SailExposure mixed=new SailExposure();mixed.addSail(-7,0,0,false);mixed.addSail(0,0,0,true);
        check(mixed.count().open()==1&&mixed.count().furled()==0,"furled front layer does not duplicate or erase deployed area");
        SailExposure closed=new SailExposure();closed.addSail(0,0,0,false);closed.addSail(1,0,0,false);
        check(closed.count().furled()==1,"furled layered windage is deduplicated too");
        double angle=359,strength=0;
        for(int tick=0;tick<48000;tick++) {
            double next=SmoothWind.angle(angle,1+SmoothWind.dailyDrift(tick));
            double delta=Math.abs(((next-angle)%360+540)%360-180);
            check(delta<=.050000001,"direction changes by at most one degree per second, including midnight");
            double s=SmoothWind.strength(strength,tick<24000?1:-1);
            check(Math.abs(s-strength)<=.00100001,"gusts and wind reversals have bounded strength changes");
            angle=next;strength=s;
        }
        check(SmoothWind.angle(359,1)>359,"359 to 1 follows the short arc");
        check(SmoothWind.angle(1,359)<1,"1 to 359 follows the short arc");
        near(SmoothWind.dailyDrift(0),SmoothWind.dailyDrift(24000),"daily drift joins continuously");
        SailResponse response=new SailResponse();double speed=0,lastDrive=0;
        for(int tick=0;tick<3600;tick++) {
            double target=response.target(18,1.0/60);
            double requested=ArcadeSailing.acceleration(new Vector3d(1,0,0),new Vector3d(speed,0,0),target,true).x;
            double drive=response.drive(requested,1.0/60);
            check(Math.abs(drive-lastDrive)<=.8/60+1e-10,"changes in thrust are bounded per second");
            speed+=drive/60;lastDrive=drive;
            if(tick==59)check(speed<.1,"unfurling cannot catapult the ship during its first second");
            if(tick==599)check(speed<5,"full rig accelerates over tens of seconds");
        }
        check(speed>16&&speed<=18,"well-rigged ships still approach cruising speed");
        near(response.target(Double.NaN,.05),response.target(Double.NaN,.05),"invalid gust leaves finite state");
        response.reset();near(response.drive(0,.05),0,"anchor/reverse clears old thrust");
        var bow=new Vector3d(1,0,0);
        double small=ArcadeSailing.targetSpeed(bow,0,1,1,25000,100000,true);
        double full=ArcadeSailing.targetSpeed(bow,0,1,16,25000,100000,true);
        check(small<3&&full>small*3,"small sail has a lower cruise speed as well as a gentle launch");
        System.out.println("SMOOTH_SAILING_PASS assertions="+checks+" finalSpeed="+speed);
    }
}
