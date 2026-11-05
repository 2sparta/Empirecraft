package ua.empirecraft.feudal;

import java.util.*;
import java.util.function.BiPredicate;

/** Projected, oriented hull geometry; neither docking nor spotting uses the centre alone. */
public final class FleetGeometry {
    public record Point(double x,double z) {}
    private FleetGeometry(){}
    private static double cross(Point a,Point b,Point c){return (b.x-a.x)*(c.z-a.z)-(b.z-a.z)*(c.x-a.x);}
    public static List<Point> hull(Collection<Point> points){
        List<Point> sorted=points.stream().distinct().sorted(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::z)).toList();
        if(sorted.size()<3)return sorted;List<Point> out=new ArrayList<>();
        for(Point p:sorted){while(out.size()>1 && cross(out.get(out.size()-2),out.get(out.size()-1),p)<=0)out.remove(out.size()-1);out.add(p);}
        int lower=out.size();for(int i=sorted.size()-2;i>=0;i--){Point p=sorted.get(i);while(out.size()>lower && cross(out.get(out.size()-2),out.get(out.size()-1),p)<=0)out.remove(out.size()-1);out.add(p);}
        out.remove(out.size()-1);return List.copyOf(out);
    }
    public static double distance(List<Point> polygon,double x,double z){
        if(polygon.isEmpty())return Double.POSITIVE_INFINITY;Point p=new Point(x,z);double best=Double.POSITIVE_INFINITY;boolean positive=false,negative=false;
        for(int i=0;i<polygon.size();i++){
            Point a=polygon.get(i),b=polygon.get((i+1)%polygon.size());double c=cross(a,b,p);positive|=c>1e-8;negative|=c< -1e-8;
            double dx=b.x-a.x,dz=b.z-a.z,d=dx*dx+dz*dz,t=d==0?0:Math.max(0,Math.min(1,((x-a.x)*dx+(z-a.z)*dz)/d));
            best=Math.min(best,Math.hypot(x-a.x-t*dx,z-a.z-t*dz));
        }
        return polygon.size()>=3 && !(positive&&negative)?0:best;
    }
    private static List<Point> clip(List<Point> in,int axis,double boundary,boolean greater){
        if(in.isEmpty())return in;List<Point> out=new ArrayList<>();Point a=in.get(in.size()-1);
        double av=(axis==0?a.x:a.z)-boundary;if(greater)av=-av;
        for(Point b:in){double bv=(axis==0?b.x:b.z)-boundary;if(greater)bv=-bv;
            if((av<=0)!=(bv<=0)){double t=av/(av-bv);out.add(new Point(a.x+(b.x-a.x)*t,a.z+(b.z-a.z)*t));}
            if(bv<=0)out.add(b);a=b;av=bv;
        }return out;
    }
    public static List<Point> intersection(List<Point> hull,double x1,double z1,double x2,double z2){
        return clip(clip(clip(clip(hull,0,x1,true),0,x2,false),1,z1,true),1,z2,false);
    }
    public static boolean docked(List<Point> hull,double x,double z,int cell,double radius,BiPredicate<Integer,Integer> owns){
        if(cell<1 || hull.isEmpty() || distance(hull,x,z)>radius+1e-7)return false;
        double minX=hull.stream().mapToDouble(Point::x).min().orElseThrow(),maxX=hull.stream().mapToDouble(Point::x).max().orElseThrow();
        double minZ=hull.stream().mapToDouble(Point::z).min().orElseThrow(),maxZ=hull.stream().mapToDouble(Point::z).max().orElseThrow();
        int a=(int)Math.floor(Math.max(minX,x-radius)/cell),b=(int)Math.floor(Math.min(maxX,x+radius)/cell);
        int c=(int)Math.floor(Math.max(minZ,z-radius)/cell),d=(int)Math.floor(Math.min(maxZ,z+radius)/cell);
        if((long)(b-a+1)*(d-c+1)>4096)return false;
        for(int cx=a;cx<=b;cx++)for(int cz=c;cz<=d;cz++)if(owns.test(cx,cz)){
            var part=intersection(hull,(double)cx*cell,(double)cz*cell,(double)(cx+1)*cell,(double)(cz+1)*cell);
            if(distance(part,x,z)<=radius+1e-7)return true;
        }return false;
    }
}
