package com.quintonc.vs_sails.ship;

import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Bounded water-only trim assistance; does not lift the ship or control its yaw. */
public final class HullStability {
    private HullStability() {}
    public static Vector3d acceleration(Quaterniondc rotation,Vector3dc omega,double immersion,
                                        double strength,double dt) {
        Vector3d result=new Vector3d();
        if(rotation==null || !rotation.isFinite() || omega==null || !omega.isFinite()
                || !Double.isFinite(immersion) || immersion<=0 || !Double.isFinite(strength) || strength<=0
                || !Double.isFinite(dt) || dt<=0 || dt>.25)return result;
        Vector3d up=rotation.transform(new Vector3d(0,1,0));
        // Let heavily capsized hulls remain capsized; fade assistance above 60 degrees.
        if(up.y<=.25)return result;
        double wet=Math.min(1,immersion/.1)*Math.min(1,(up.y-.25)/.25);
        double k=Math.min(20,strength)*wet, damping=2*Math.sqrt(k);
        double tilt=Math.atan2(Math.hypot(up.x,up.z),up.y);
        double error=Math.max(0,tilt-Math.toRadians(2));
        if(error>0)result.set(-up.z,0,up.x).normalize().mul(error*k);
        result.add(-damping*omega.x(),0,-damping*omega.z()).div(1+damping*dt+k*dt*dt);
        if(result.lengthSquared()>2.25)result.normalize().mul(1.5);
        return result;
    }
}
