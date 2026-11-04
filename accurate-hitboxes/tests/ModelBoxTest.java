import java.util.*;
import org.joml.Vector3d;
import org.joml.Matrix3d;
import net.devra.accuratehitboxes.empirecraft.ModelBox;

public final class ModelBoxTest {
    static int checks;
    static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    static ModelBox cube(double x,double y,double z,double size){return new ModelBox(new Vector3d(x,y,z),new Vector3d(size,0,0),new Vector3d(0,size,0),new Vector3d(0,0,size));}
    public static void main(String[] args){
        var weapon=cube(0,0,0,1);check(weapon.intersects(cube(1.5,0,0,1)),"overlapping cubes intersect");check(!weapon.intersects(cube(3,0,0,1)),"distant cubes miss");
        double s=Math.sqrt(.5);var fin=new ModelBox(new Vector3d(),new Vector3d(5*s,0,5*s),new Vector3d(0,1e-5,0),new Vector3d(-.05*s,0,.05*s));
        check(fin.intersects(cube(2,0,2,.2)),"thin rotated fin is hittable");
        check(!fin.intersects(cube(3,0,-3,.1)),"empty corner of fin envelope is not a hit");
        var far=cube(29_999_999,75,-29_999_999,1);check(far.intersects(cube(29_999_999.5,75,-29_999_999,.2)),"world-border precision is retained");
        var tail=new ModelBox(new Vector3d(0,2.4,26),new Vector3d(3,0,0),new Vector3d(0,.17,0),new Vector3d(0,0,1.7));
        var blade=cube(0,2.4,27.8,.5);check(tail.intersects(blade),"Blue Whale tail outside central body intersects weapon");
        var contacts=tail.contacts(blade,new Vector3d(0,2.4,30));check(!contacts.isEmpty(),"tail contact points exist");
        for(var point:contacts)check(tail.contains(point)&&blade.contains(point),"every contact belongs to both real shapes");
        Random random=new Random(20941);
        for(int i=0;i<500;i++){
            var rotation=new Matrix3d().rotationXYZ(random.nextDouble()*6,random.nextDouble()*6,random.nextDouble()*6);
            var box=new ModelBox(new Vector3d(random.nextDouble()*4-2,random.nextDouble()*4-2,random.nextDouble()*4-2),
                    rotation.transform(new Vector3d(1.5,0,0)),rotation.transform(new Vector3d(0,.15,0)),rotation.transform(new Vector3d(0,0,.7)));
            check(box.intersects(weapon)==weapon.intersects(box),"SAT symmetry over rotated animated parts");
            var points=box.contacts(weapon,new Vector3d(0,0,-3));
            check(!box.intersects(weapon)||!points.isEmpty(),"every intersection has usable contact geometry");
            for(var p:points)check(box.contains(p)&&weapon.contains(p),"rotated contact remains inside actual model and weapon");
        }
        System.out.println("WHALE_MODEL_GEOMETRY_PASS assertions="+checks);
    }
}
