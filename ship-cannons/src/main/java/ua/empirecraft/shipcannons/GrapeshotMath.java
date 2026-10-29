package ua.empirecraft.shipcannons;

import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class GrapeshotMath {
    private GrapeshotMath() {}
    /** Equal-area disk sampling, including vertical shots. Spread is tan(half angle). */
    public static Vector3d pellet(Vector3dc velocity,int index,int count,double spread,double phase) {
        if(velocity.lengthSquared()<1e-12 || count<=0)return new Vector3d();
        Vector3d forward=new Vector3d(velocity).normalize();
        Vector3d right=new Vector3d(forward).cross(Math.abs(forward.y)>.99?new Vector3d(1,0,0):new Vector3d(0,1,0)).normalize();
        Vector3d up=new Vector3d(forward).cross(right).normalize();
        double r=Math.sqrt((index+.5)/count)*spread,theta=phase+index*2.399963229728653;
        return forward.fma(r*Math.cos(theta),right).fma(r*Math.sin(theta),up).normalize().mul(velocity.length()*1.5);
    }
}
