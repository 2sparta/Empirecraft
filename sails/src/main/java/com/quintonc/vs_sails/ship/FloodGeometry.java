package com.quintonc.vs_sails.ship;

import java.util.*;

/** Gravity-space geometry of a unit voxel. Shared by transport, rendering and immersion. */
public final class FloodGeometry {
    private FloodGeometry() {}
    public record Point(double x,double y,double z) {
        public double dot(double a,double b,double c){return x*a+y*b+z*c;}
        Point lerp(Point p,double t){return new Point(x+(p.x-x)*t,y+(p.y-y)*t,z+(p.z-z)*t);}
    }
    public record Face(int direction,List<Point> points) {}
    private static final Point[] CORNERS={new Point(0,0,0),new Point(0,0,1),new Point(0,1,0),new Point(0,1,1),
            new Point(1,0,0),new Point(1,0,1),new Point(1,1,0),new Point(1,1,1)};
    // Minecraft Direction order: down, up, north, south, west, east.
    private static final int[][] FACES={{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3},{0,1,3,2},{4,6,7,5}};

    public static double plane(double depth,double x,double y,double z){return Math.min(0,x)+Math.min(0,y)+Math.min(0,z)+depth;}

    public static List<Point> clip(List<Point> input,double x,double y,double z,double plane) {
        List<Point> out=new ArrayList<>();if(input.isEmpty())return out;
        Point previous=input.get(input.size()-1);double a=previous.dot(x,y,z)-plane;
        for(Point p:input){
            double b=p.dot(x,y,z)-plane;
            if((a<=1e-9)!=(b<=1e-9))out.add(previous.lerp(p,a/(a-b)));
            if(b<=1e-9)out.add(p);previous=p;a=b;
        }
        return out;
    }

    /** Closed polyhedron with a level free surface, including at 90/180 degree heel. */
    public static List<Face> mesh(double x,double y,double z,double depth) {
        if(depth<=0)return List.of();double plane=plane(depth,x,y,z);List<Face> result=new ArrayList<>();List<Point> cap=new ArrayList<>();
        for(int d=0;d<FACES.length;d++){
            List<Point> square=new ArrayList<>();for(int at:FACES[d])square.add(CORNERS[at]);
            var polygon=clip(square,x,y,z,plane);
            if(polygon.size()>=3)result.add(new Face(d,List.copyOf(polygon)));
            for(Point p:polygon)if(Math.abs(p.dot(x,y,z)-plane)<1e-7 && cap.stream().noneMatch(q->Math.abs(q.x-p.x)+Math.abs(q.y-p.y)+Math.abs(q.z-p.z)<1e-7))cap.add(p);
        }
        if(depth<Math.abs(x)+Math.abs(y)+Math.abs(z)-1e-7 && cap.size()>=3){
            double cx=0,cy=0,cz=0;for(Point p:cap){cx+=p.x;cy+=p.y;cz+=p.z;}cx/=cap.size();cy/=cap.size();cz/=cap.size();
            double ux=Math.abs(y)<.9?z:0,uy=Math.abs(y)<.9?0:-z,uz=Math.abs(y)<.9?-x:y;
            double len=Math.sqrt(ux*ux+uy*uy+uz*uz);ux/=len;uy/=len;uz/=len;
            final double ax=ux,ay=uy,az=uz,bx=y*uz-z*uy,by=z*ux-x*uz,bz=x*uy-y*ux,ox=cx,oy=cy,oz=cz;
            cap.sort(Comparator.comparingDouble(p->Math.atan2((p.x-ox)*bx+(p.y-oy)*by+(p.z-oz)*bz,(p.x-ox)*ax+(p.y-oy)*ay+(p.z-oz)*az)));
            result.add(new Face(-1,List.copyOf(cap)));
        }
        return List.copyOf(result);
    }

    /** Exact centroid of the clipped polyhedron, for the water's load on the hull. */
    public static Point centroid(List<Face> faces) {
        double volume=0,x=0,y=0,z=0;
        for(Face face:faces){var p=face.points;Point a=p.get(0);
            for(int i=1;i+1<p.size();i++){Point b=p.get(i),c=p.get(i+1);
                double v=(a.x*(b.y*c.z-b.z*c.y)+a.y*(b.z*c.x-b.x*c.z)+a.z*(b.x*c.y-b.y*c.x))/6;
                volume+=v;x+=v*(a.x+b.x+c.x)/4;y+=v*(a.y+b.y+c.y)/4;z+=v*(a.z+b.z+c.z)/4;
            }
        }
        return volume>1e-9?new Point(x/volume,y/volume,z/volume):new Point(.5,.5,.5);
    }

    /** Volume below a plane measured from the lowest corner of a rotated unit cube. */
    public static double fraction(double depth,double x,double y,double z) {
        double[] axes={Math.abs(x),Math.abs(y),Math.abs(z)};
        double span=axes[0]+axes[1]+axes[2];
        if(depth<=0)return 0;
        if(depth>=span)return 1;
        // Symmetry avoids cancellation near the full end of the distribution.
        if(depth>span*.5)return 1-fraction(span-depth,x,y,z);
        double[] a=new double[3];int count=0;double product=1;
        for(double axis:axes)if(axis>1e-6){a[count++]=axis;product*=axis;}
        if(count==0)return 0;
        double sum=0;
        for(int mask=0;mask<(1<<count);mask++){
            double d=depth;for(int i=0;i<count;i++)if((mask&(1<<i))!=0)d-=a[i];
            if(d>0)sum+=(Integer.bitCount(mask)%2==0?1:-1)*Math.pow(d,count);
        }
        return Math.max(0,Math.min(1,sum/(product*(count==3?6:count==2?2:1))));
    }

    /** Exact (to floating point tolerance) plane heights for the eight finite measures. */
    public static double[] depths(double x,double y,double z) {
        double span=Math.abs(x)+Math.abs(y)+Math.abs(z);double[] result=new double[9];result[8]=span;
        for(int q=1;q<8;q++){
            double low=0,high=span;
            for(int i=0;i<42;i++){double mid=(low+high)*.5;if(fraction(mid,x,y,z)<q/8.0)low=mid;else high=mid;}
            result[q]=(low+high)*.5;
        }
        return result;
    }
}
