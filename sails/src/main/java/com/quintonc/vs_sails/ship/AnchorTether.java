package com.quintonc.vs_sails.ship;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** A tension-only, damped rope. It never pushes or freezes a ship. */
public final class AnchorTether {
    private AnchorTether() {}

    /** A soft horizontal berth plus a slack vertical chain, in world coordinates. */
    public static Vector3d mooringForce(Vector3dc point, Vector3dc bottom, Vector3dc velocity,
                                         double length, double mass, double radius) {
        if(point==null || bottom==null || velocity==null || !point.isFinite() || !bottom.isFinite()
                || !velocity.isFinite() || !Double.isFinite(length) || length<0 || length>64
                || !Double.isFinite(mass) || mass<=0 || !Double.isFinite(radius) || radius<=0 || radius>8)
            return new Vector3d();
        double x=point.x()-bottom.x(),z=point.z()-bottom.z(),distance=Math.hypot(x,z);
        Vector3d force=new Vector3d(-velocity.x()*.35,0,-velocity.z()*.35);
        if(distance>radius) {
            double outward=(velocity.x()*x+velocity.z()*z)/distance;
            double pull=Math.min(4,(distance-radius)*2+Math.max(0,outward)*1.8);
            force.add(-pull*x/distance,0,-pull*z/distance);
        }
        if(force.lengthSquared()>16)force.normalize().mul(4);
        force.mul(mass);
        // Slack permits heave and roll; tension cannot push the ship away from the seabed.
        return force.add(force(point,bottom,velocity,length+.45,mass));
    }

    public static Vector3d force(Vector3dc point, Vector3dc target, Vector3dc velocity,
                                double ropeLength, double mass) {
        if(point==null || target==null || velocity==null)return new Vector3d();
        Vector3d delta = target.sub(point, new Vector3d());
        double distance = delta.length();
        if (!delta.isFinite() || !velocity.isFinite() || !Double.isFinite(mass)
                || !Double.isFinite(ropeLength) || ropeLength < 0 || mass <= 0
                || distance <= ropeLength || distance < 1e-6) return new Vector3d();
        delta.div(distance);
        double tension = Math.max(0, Math.min(mass * 4, mass * ((distance - ropeLength) * .8 - velocity.dot(delta) * 1.2)));
        return delta.mul(tension);
    }
}
