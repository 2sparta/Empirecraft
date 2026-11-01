package ua.empirecraft.shipphysics;

import java.util.*;
import java.util.function.Predicate;

/** Bounded, face-connected stress spread inside a sphere; air never carries an impact. */
public final class ImpactSpread {
    private ImpactSpread() {}
    public record Point(int x, int y, int z) {
        int distanceSquared(Point p) {
            int dx=x-p.x, dy=y-p.y, dz=z-p.z;
            return dx*dx+dy*dy+dz*dz;
        }
    }
    public record Share(Point point, double fraction) {}
    public static int radius(double energy, double toughness, int configuredDepth) {
        if (!Double.isFinite(energy) || energy<=0 || !Double.isFinite(toughness) || toughness<=0) return 0;
        // At most 257 candidate positions per contact patch.
        return Math.min(Math.min(4, Math.max(0, configuredDepth-1)),
                (int)Math.ceil(Math.cbrt(energy/toughness)));
    }
    public static List<Share> cells(Point origin, int radius, Predicate<Point> solid) {
        radius=Math.max(0,Math.min(4,radius));
        int radiusSquared=radius*radius;
        var seen=new HashSet<Point>();
        var queue=new ArrayDeque<Point>();
        var connected=new ArrayList<Point>();
        seen.add(origin);queue.add(origin);
        int[][] steps={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while(!queue.isEmpty()) {
            Point p=queue.removeFirst();
            if(!solid.test(p))continue;
            connected.add(p);
            for(int[] step:steps) {
                Point next=new Point(p.x+step[0],p.y+step[1],p.z+step[2]);
                if(next.distanceSquared(origin)<=radiusSquared && seen.add(next))queue.addLast(next);
            }
        }
        connected.sort(Comparator.comparingInt((Point p)->p.distanceSquared(origin))
                .thenComparingInt(Point::x).thenComparingInt(Point::y).thenComparingInt(Point::z));
        double total=0;
        for(Point p:connected)total+=1.0/(1+p.distanceSquared(origin));
        var result=new ArrayList<Share>();
        for(Point p:connected)result.add(new Share(p,1.0/(1+p.distanceSquared(origin))/total));
        return List.copyOf(result);
    }
}
