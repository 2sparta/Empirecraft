package ua.empirecraft.shipcannons;

import org.joml.Matrix3dc;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Momentum in N s, angular impulse in N m s, and angular velocities in radians/s. */
public final class RecoilMath {
    public record Settings(double rollMultiplier,double minimumLever,double perShotAngularKick,double angularSpeedLimit,double perShotLinearKick,double pulseSeconds) {
        public Settings {
            if(!Double.isFinite(rollMultiplier+minimumLever+perShotAngularKick+angularSpeedLimit+perShotLinearKick+pulseSeconds)
                || rollMultiplier<1 || minimumLever<0 || perShotAngularKick<=0 || angularSpeedLimit<=0 || perShotLinearKick<=0 || pulseSeconds<=0)
                throw new IllegalArgumentException("Invalid recoil settings");
        }
    }
    public static final Settings DEFAULT=new Settings(20,1,Math.toRadians(4),Math.toRadians(20),.75,.12);
    private RecoilMath() {}
    public static double momentum(double mass,double blocksPerTick,double multiplier,double cap) {
        if(!Double.isFinite(mass) || !Double.isFinite(blocksPerTick) || !Double.isFinite(multiplier) || !Double.isFinite(cap) || mass<=0 || blocksPerTick<=0 || multiplier<=0 || cap<=0)return 0;
        return Math.min(cap,mass*blocksPerTick*20*multiplier);
    }
    public static Vector3d angularImpulse(Vector3dc arm,Vector3dc impulse,Vector3dc up,Settings settings) {
        Vector3d normal=new Vector3d(up).normalize();
        Vector3d horizontal=new Vector3d(impulse).fma(-impulse.dot(normal),normal);
        double lever=Math.max(settings.minimumLever(),Math.abs(arm.dot(normal)));
        Vector3d result=new Vector3d(arm).cross(impulse);
        // A virtual lever above the COM gives side guns a consistent heel, including low gun decks.
        // Preserve the true yaw moment and the moment of the vertical component of the shot.
        return result.fma(settings.rollMultiplier()*lever-arm.dot(normal),new Vector3d(normal).cross(horizontal));
    }
    public static Vector3d angularVelocity(Matrix3dc inertia,Vector3dc impulse) {
        if(inertia==null || !inertia.isFinite() || !(inertia.determinant()>1e-18))return new Vector3d();
        Vector3d result=new Matrix3d(inertia).invert().transform(new Vector3d(impulse));
        return result.isFinite()?result:new Vector3d();
    }
    /** Limit a new kick, preserving the body's existing motion and opposite kicks. */
    public static double rateScale(Vector3dc current,Vector3dc kick,double maximum) {
        double a=kick.lengthSquared();if(a<1e-18)return 1;
        double b=2*current.dot(kick),c=current.lengthSquared()-maximum*maximum;
        if(c>0)return b<0 && b+a<=0?1:0;
        double root=(-b+Math.sqrt(Math.max(0,b*b-4*a*c)))/(2*a);
        return Math.max(0,Math.min(1,root));
    }
}
