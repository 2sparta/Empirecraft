package ua.empirecraft.shipcannons;
import org.joml.Vector3d;
import org.joml.Vector3dc;
/** Velocities are relative to the deck; VS supplies its rigid motion separately. */
public final class DeckMath {
    private DeckMath() {}
    public static Vector3d tangent(Vector3dc v,Vector3dc n) {return new Vector3d(v).fma(-v.dot(n),n);}
    public static Vector3d input(Vector3dc v,Vector3dc n) {
        Vector3d t=tangent(v,n);return t.lengthSquared()>1e-12?t.mul(v.length()/t.length()):t;
    }
    public static Vector3d contactVelocity(Vector3dc v,Vector3dc n) {
        return tangent(v,n).mul(.05).fma(Math.max(-.08,Math.min(0,v.dot(n))-.04),n);
    }
    public static double localYaw(org.joml.Quaterniondc rotation,double worldYaw) {
        double angle=Math.toRadians(worldYaw);
        var v=rotation.transformInverse(new Vector3d(-Math.sin(angle),0,Math.cos(angle)));
        return Math.toDegrees(-Math.atan2(v.x,v.z));
    }
}
