package com.quintonc.vs_sails.ship;

import java.util.*;

/** Subtract convex hull volumes from an ocean face, retaining interpolated vertex data. */
public final class WaterOcclusion {
    public record Vertex(double x,double y,double z,double[] data) {
        Vertex mix(Vertex b,double t){
            double[] next=new double[data.length];
            for(int i=0;i<next.length;i++)next[i]=data[i]+(b.data[i]-data[i])*t;
            return new Vertex(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,next);
        }
    }
    /** The inside half-space is x*vx + y*vy + z*vz <= limit. */
    public record Plane(double x,double y,double z,double limit) {
        double distance(Vertex v){return x*v.x+y*v.y+z*v.z-limit;}
    }
    private WaterOcclusion(){}

    public static List<List<Vertex>> subtract(List<Vertex> polygon,List<Plane> volume){
        List<List<Vertex>> outside=new ArrayList<>();List<Vertex> remaining=polygon;
        for(Plane plane:volume){
            var part=clip(remaining,plane,false);if(part.size()>=3)outside.add(part);
            remaining=clip(remaining,plane,true);if(remaining.size()<3)break;
        }
        return outside;
    }
    private static List<Vertex> clip(List<Vertex> polygon,Plane plane,boolean inside){
        List<Vertex> result=new ArrayList<>();if(polygon.isEmpty())return result;
        Vertex previous=polygon.get(polygon.size()-1);double a=plane.distance(previous);
        for(Vertex current:polygon){
            double b=plane.distance(current);
            boolean from=inside?a<=0:a>0,to=inside?b<=0:b>0;
            if(from!=to)result.add(previous.mix(current,a/(a-b)));
            if(to)result.add(current);
            previous=current;a=b;
        }
        return result;
    }
}
