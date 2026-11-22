package ua.empirecraft.siegeworks.vs;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;
/** Scoped native penetration/crater coordinates. Never move a live projectile into ship storage. */
public final class Frames implements AutoCloseable {
 private static final ThreadLocal<Ship> CURRENT=new ThreadLocal<>();private final Ship previous;
 private Frames(Ship ship){previous=CURRENT.get();if(ship==null)CURRENT.remove();else CURRENT.set(ship);}
 public static Frames enter(Ship ship){return new Frames(ship);}public static Ship current(){return CURRENT.get();}
 public static Vec3 world(Ship s,Vec3 v){if(s==null||v==null)return v;var p=s.getShipToWorld().transformPosition(new Vector3d(v.x,v.y,v.z));return new Vec3(p.x,p.y,p.z);}
 public static Vec3 local(Ship s,Vec3 v){if(s==null||v==null)return v;var p=s.getWorldToShip().transformPosition(new Vector3d(v.x,v.y,v.z));return new Vec3(p.x,p.y,p.z);}
 public static Vec3 direction(Ship s,Vec3 v,boolean inverse){if(s==null||v==null)return v;var p=new Vector3d(v.x,v.y,v.z);if(inverse)s.getTransform().getShipToWorldRotation().transformInverse(p);else s.getTransform().getShipToWorldRotation().transform(p);return new Vec3(p.x,p.y,p.z);}
 @Override public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
}
