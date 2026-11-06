package ua.empirecraft.engagement;
import org.joml.Vector3d;
public final class RamMotionTest {
    static RamCombat.Motion motion(double x){return new RamCombat.Motion(new Vector3d(),new Vector3d(x,0,0),new Vector3d());}
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    public static void main(String[] args){
        var zero=new Vector3d();var normal=new Vector3d(1,0,0);
        var bounce=new RamCombat.Motions(10,20,motion(-1),motion(2),motion(6),motion(0));
        check(RamCombat.approach(bounce,10,10,zero,normal)==6,"Pre-impact attacker remains identified after rebound");
        check(RamCombat.approach(bounce,10,20,zero,normal)==0,"Stationary defender never becomes an attacker because another ship broke its own bow");
        var reversed=new RamCombat.Motions(20,10,motion(2),motion(-1),motion(0),motion(6));
        check(RamCombat.approach(reversed,10,10,zero,normal)==6,"Sorted native pair IDs preserve collision direction");
        var headOn=new RamCombat.Motions(10,20,motion(6),motion(-4),motion(6),motion(-4));
        check(RamCombat.approach(headOn,10,10,zero,normal)==6&&RamCombat.approach(headOn,10,20,zero,normal)==4,"Both moving bows can independently initiate a head-on ram");
        var together=new RamCombat.Motions(10,20,motion(3),motion(3),motion(3),motion(3));
        check(RamCombat.approach(together,10,10,zero,normal)==0,"Shared motion without closing cannot initiate a ram");
        var spin=new RamCombat.Motion(new Vector3d(),new Vector3d(),new Vector3d(0,0,3));var still=motion(0);var rotating=new RamCombat.Motions(10,20,spin,still,spin,still);
        check(RamCombat.approach(rotating,10,10,new Vector3d(1,0,0),new Vector3d(0,1,0))==3,"A rotating hull tip uses contact-point angular velocity");
        System.out.println("RAM_MOTION_PASS scenarios=6");
    }
}
