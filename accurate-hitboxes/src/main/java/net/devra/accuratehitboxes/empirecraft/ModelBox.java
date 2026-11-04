package net.devra.accuratehitboxes.empirecraft;

import java.util.*;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/** Affine model cube. All world-space calculations retain double precision. */
public final class ModelBox {
    public final Vector3d center;
    private final Vector3d[] edges;
    private final Matrix3d inverse;
    public ModelBox(Vector3d center,Vector3d x,Vector3d y,Vector3d z){
        this.center=new Vector3d(center);edges=new Vector3d[]{new Vector3d(x),new Vector3d(y),new Vector3d(z)};
        Matrix3d basis=new Matrix3d(x,y,z);
        if(!center.isFinite()||!x.isFinite()||!y.isFinite()||!z.isFinite()||Math.abs(basis.determinant())<1e-15)
            throw new IllegalArgumentException("Invalid model cube");
        inverse=basis.invert();
    }
    public boolean contains(Vector3d point){
        var p=inverse.transform(new Vector3d(point).sub(center));
        return Math.abs(p.x)<=1.000001&&Math.abs(p.y)<=1.000001&&Math.abs(p.z)<=1.000001;
    }
    public Vector3d closest(Vector3d point){
        var p=inverse.transform(new Vector3d(point).sub(center));
        return world(Math.max(-1,Math.min(1,p.x)),Math.max(-1,Math.min(1,p.y)),Math.max(-1,Math.min(1,p.z)));
    }
    public double[] bounds(){
        double x=0,y=0,z=0;for(var e:edges){x+=Math.abs(e.x);y+=Math.abs(e.y);z+=Math.abs(e.z);}
        return new double[]{center.x-x,center.y-y,center.z-z,center.x+x,center.y+y,center.z+z};
    }
    public boolean intersects(ModelBox other){
        for(int i=0;i<3;i++){
            if(separated(other,edges[(i+1)%3].cross(edges[(i+2)%3],new Vector3d())))return false;
            if(separated(other,other.edges[(i+1)%3].cross(other.edges[(i+2)%3],new Vector3d())))return false;
            for(int j=0;j<3;j++)if(separated(other,edges[i].cross(other.edges[j],new Vector3d())))return false;
        }
        return true;
    }
    private boolean separated(ModelBox other,Vector3d axis){
        double norm=axis.length();if(norm<1e-12)return false;
        double support=0;for(int i=0;i<3;i++)support+=Math.abs(axis.dot(edges[i]))+Math.abs(axis.dot(other.edges[i]));
        return Math.abs(axis.dot(other.center.sub(center,new Vector3d())))>support+1e-7*norm;
    }
    /** Points belong to the actual intersection, including rotated edge/face contacts. */
    public List<Vector3d> contacts(ModelBox other,Vector3d origin){
        if(!intersects(other))return List.of();
        List<Vector3d> vertices=new ArrayList<>(),points=new ArrayList<>();
        clipEdges(this,other,origin,vertices,points);clipEdges(other,this,origin,vertices,points);
        add(points,closest(origin),other);add(points,other.closest(origin),this);
        projectFaces(this,other,origin,points);projectFaces(other,this,origin,points);
        points.addAll(vertices);
        // New edges can be intersections of two face interiors. Every segment
        // between intersection vertices remains inside both convex cubes.
        for(int i=0;i<vertices.size();i++)for(int j=i+1;j<vertices.size();j++)points.add(onSegment(origin,vertices.get(i),vertices.get(j)));
        points.sort(Comparator.comparingDouble(origin::distanceSquared));return points;
    }
    private static void projectFaces(ModelBox a,ModelBox b,Vector3d origin,List<Vector3d> out){
        for(int i=0;i<3;i++){
            Vector3d normal=a.edges[(i+1)%3].cross(a.edges[(i+2)%3],new Vector3d());
            for(int sign:new int[]{-1,1}){
                var face=a.center.add(a.edges[i].mul(sign,new Vector3d()),new Vector3d());
                var p=new Vector3d(origin).fma(face.sub(origin).dot(normal)/normal.lengthSquared(),normal);
                if(a.contains(p))add(out,p,b);
            }
        }
    }
    private static void clipEdges(ModelBox a,ModelBox b,Vector3d origin,List<Vector3d> vertices,List<Vector3d> points){
        Vector3d[] v=a.vertices();
        for(int i=0;i<8;i++)for(int axis=0;axis<3;axis++)if((i&(1<<axis))==0){
            Vector3d from=v[i],to=v[i|(1<<axis)];double[] range=b.clip(from,to);if(range==null)continue;
            var start=from.lerp(to,range[0],new Vector3d());var end=from.lerp(to,range[1],new Vector3d());
            unique(vertices,start);unique(vertices,end);points.add(onSegment(origin,start,end));
        }
    }
    private double[] clip(Vector3d from,Vector3d to){
        Vector3d a=inverse.transform(from.sub(center,new Vector3d())),b=inverse.transform(to.sub(center,new Vector3d()));
        double lo=0,hi=1;
        for(int i=0;i<3;i++){
            double start=a.get(i),delta=b.get(i)-start;
            if(Math.abs(delta)<1e-12){if(start < -1.000001||start>1.000001)return null;continue;}
            double enter=(-1-start)/delta,leave=(1-start)/delta;
            if(enter>leave){double swap=enter;enter=leave;leave=swap;}
            lo=Math.max(lo,enter);hi=Math.min(hi,leave);if(lo>hi+1e-9)return null;
        }
        return new double[]{Math.max(0,Math.min(1,lo)),Math.max(0,Math.min(1,hi))};
    }
    private Vector3d[] vertices(){
        Vector3d[] v=new Vector3d[8];for(int i=0;i<8;i++)v[i]=world((i&1)==0?-1:1,(i&2)==0?-1:1,(i&4)==0?-1:1);return v;
    }
    private Vector3d world(double x,double y,double z){return new Vector3d(center).fma(x,edges[0]).fma(y,edges[1]).fma(z,edges[2]);}
    private static Vector3d onSegment(Vector3d p,Vector3d a,Vector3d b){
        var d=b.sub(a,new Vector3d());double length=d.lengthSquared();
        return new Vector3d(a).fma(length<1e-20?0:Math.max(0,Math.min(1,p.sub(a,new Vector3d()).dot(d)/length)),d);
    }
    private static void add(List<Vector3d> out,Vector3d p,ModelBox inside){if(inside.contains(p))out.add(p);}
    private static void unique(List<Vector3d> out,Vector3d p){for(var old:out)if(old.distanceSquared(p)<1e-12)return;out.add(p);}
}
