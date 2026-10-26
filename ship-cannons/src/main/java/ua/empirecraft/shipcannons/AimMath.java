package ua.empirecraft.shipcannons;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
/** Supplementaries stores the opposite of Minecraft's look direction in the barrel. */
public final class AimMath {
    private AimMath() {}
    public static Vector3d direction(double yaw,double pitch) {
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch),c=Math.cos(p);
        return new Vector3d(Math.sin(y)*c,Math.sin(p),-Math.cos(y)*c);
    }
    public static double yaw(Vector3dc d){return Math.toDegrees(Math.atan2(d.x(),-d.z()));}
    public static double pitch(Vector3dc d){return Math.toDegrees(Math.atan2(d.y(),Math.hypot(d.x(),d.z())));}
    public static Vector3d localDirection(Quaterniondc rotation,double worldYaw,double worldPitch) {
        return new Quaterniond(rotation).conjugate().transform(direction(worldYaw,worldPitch));
    }
}
