import java.lang.reflect.*;import java.util.*;
import org.joml.Matrix4d;import org.joml.Quaterniond;import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;
import net.minecraft.world.phys.Vec3;
import ua.empirecraft.siegeworks.vs.Frames;
public final class FramesTest {
 static int checks;static void check(boolean good,String s){checks++;if(!good)throw new AssertionError(s);}
 public static void main(String[]args)throws Exception{
  var rotation=new Quaterniond().rotateXYZ(.13,.7,-.2);var matrix=new Matrix4d().translation(12,81,-35).rotate(rotation).translate(-286052000,-64,286052000);var inverse=new Matrix4d(matrix).invert();
  var transformType=Ship.class.getMethod("getTransform").getReturnType();Object transform=Proxy.newProxyInstance(FramesTest.class.getClassLoader(),new Class[]{transformType},(o,m,a)->switch(m.getName()){case "getShipToWorldRotation","getRotation"->rotation;default->throw new UnsupportedOperationException(m.getName());});
  Ship ship=(Ship)Proxy.newProxyInstance(FramesTest.class.getClassLoader(),new Class[]{Ship.class},(o,m,a)->switch(m.getName()){case "getTransform"->transform;case "getShipToWorld"->matrix;case "getWorldToShip"->inverse;default->throw new UnsupportedOperationException(m.getName());});
  var random=new Random(4);for(int i=0;i<10000;i++){var p=new Vec3(286052000+random.nextDouble()*32,64+random.nextDouble()*16,-286052000+random.nextDouble()*32);check(Frames.local(ship,Frames.world(ship,p)).distanceTo(p)<1e-5,"Storage/world round trip");var v=new Vec3(random.nextDouble()*8,random.nextDouble()*8,random.nextDouble()*8);check(Frames.direction(ship,Frames.direction(ship,v,false),true).distanceTo(v)<1e-10,"Velocity/normal round trip");check(Math.abs(v.length()-Frames.direction(ship,v,false).length())<1e-10,"Rotation preserves speed/energy");}
  check(Frames.current()==null,"Empty frame");try(var a=Frames.enter(ship)){check(Frames.current()==ship,"Ship frame");try(var b=Frames.enter(null)){check(Frames.current()==null,"World frame");}check(Frames.current()==ship,"Nested frame restored");try(var b=Frames.enter(null)){throw new IllegalArgumentException("expected");}catch(IllegalArgumentException expected){}check(Frames.current()==ship,"Exception restores caller frame");var clean=new boolean[1];Thread thread=new Thread(()->clean[0]=Frames.current()==null);thread.start();thread.join();check(clean[0],"Frame isolated from worker threads");}check(Frames.current()==null,"No leaked frame");
  System.out.println("SIEGE_VS_FRAMES_PASS checks="+checks+" roundTrips=10000 nested=true exceptions=true threadIsolation=true");
 }
}
