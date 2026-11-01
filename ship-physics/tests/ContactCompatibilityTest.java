import ua.empirecraft.shipphysics.CollisionDamage;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.lang.reflect.Proxy;

/** VS 2.4.11 PhysShip AABB accessors deliberately throw; contacts must never call them. */
public final class ContactCompatibilityTest {
    static PhysShip body(double y){
        var transform=(ShipTransform)Proxy.newProxyInstance(ShipTransform.class.getClassLoader(),new Class[]{ShipTransform.class},(p,m,a)->{
            if(m.getName().equals("getPositionInWorld"))return new Vector3d(0,y,0);
            throw new AssertionError("Unexpected transform access "+m.getName());
        });
        return (PhysShip)Proxy.newProxyInstance(PhysShip.class.getClassLoader(),new Class[]{PhysShip.class},(p,m,a)->{
            if(m.getName().equals("getTransform"))return transform;
            throw new AssertionError("Unsupported physics body access "+m.getName());
        });
    }
    public static void main(String[] args)throws Exception{
        var method=CollisionDamage.class.getDeclaredMethod("orientedNormal",PhysShip.class,PhysShip.class,Vector3dc.class,Vector3dc.class);
        method.setAccessible(true);int checks=0;
        for(double sign:new double[]{-1,1}){
            Vector3d n=(Vector3d)method.invoke(null,body(10),null,new Vector3d(0,9,0),new Vector3d(0,sign,0));
            if(n.y!=-1)throw new AssertionError("ship into bed normal");checks++;
            n=(Vector3d)method.invoke(null,null,body(10),new Vector3d(0,9,0),new Vector3d(0,sign,0));
            if(n.y!=1)throw new AssertionError("bed into ship normal");checks++;
            n=(Vector3d)method.invoke(null,body(10),body(20),new Vector3d(0,15,0),new Vector3d(0,sign,0));
            if(n.y!=1)throw new AssertionError("ship pair normal");checks++;
        }
        System.out.println("CONTACT_COMPATIBILITY_PASS assertions="+checks);
    }
}
