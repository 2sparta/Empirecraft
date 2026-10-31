package ua.posters;
import java.util.*;import org.joml.Matrix4dc;import org.joml.Vector3d;
/** Static model surfaces, read from installed mods; no client renderer is loaded on a server. */
public final class Mesh {
    public record Point(double x,double y,double z) {
        public Point transform(Matrix4dc m){Vector3d v=new Vector3d(x,y,z);m.transformPosition(v);return new Point(v.x,v.y,v.z);}
    }
    public record Vertex(Point p,double u,double v) {public Vertex transform(Matrix4dc m){return new Vertex(p.transform(m),u,v);}}
    public record Mask(int width,int height,int[] argb) {
        public boolean visible(double u,double v){int x=Math.floorMod((int)Math.floor(u*width),width),y=Math.floorMod((int)Math.floor(v*height),height);return (argb[x+y*width]>>>24)>=128;}
    }
    public record Triangle(Vertex a,Vertex b,Vertex c,int part,int rgb,Mask mask) {
        public Triangle transform(Matrix4dc m,int part,int rgb){return new Triangle(a.transform(m),b.transform(m),c.transform(m),part,rgb==Integer.MIN_VALUE?this.rgb:rgb,mask);}
    }
    public static List<Triangle> box(double x0,double y0,double z0,double x1,double y1,double z1,int part,int rgb){
        List<Triangle> out=new ArrayList<>();for(String face:List.of("north","east","south","west","up","down"))out.addAll(face(face,x0,y0,z0,x1,y1,z1,new double[]{0,0,16,16},0,part,rgb,null));return out;
    }
    public static List<Triangle> face(String face,double x0,double y0,double z0,double x1,double y1,double z1,double[] uv,int rotation,int part,int rgb,Mask mask){
        Point[] p=switch(face){
            case "north"->new Point[]{new Point(x1,y1,z0),new Point(x1,y0,z0),new Point(x0,y0,z0),new Point(x0,y1,z0)};
            case "south"->new Point[]{new Point(x0,y1,z1),new Point(x0,y0,z1),new Point(x1,y0,z1),new Point(x1,y1,z1)};
            case "west"->new Point[]{new Point(x0,y1,z0),new Point(x0,y0,z0),new Point(x0,y0,z1),new Point(x0,y1,z1)};
            case "east"->new Point[]{new Point(x1,y1,z1),new Point(x1,y0,z1),new Point(x1,y0,z0),new Point(x1,y1,z0)};
            case "up"->new Point[]{new Point(x0,y1,z0),new Point(x0,y1,z1),new Point(x1,y1,z1),new Point(x1,y1,z0)};
            case "down"->new Point[]{new Point(x0,y0,z1),new Point(x0,y0,z0),new Point(x1,y0,z0),new Point(x1,y0,z1)};
            default->throw new IllegalArgumentException("face");
        };
        double[][] coords={{uv[0]/16,uv[1]/16},{uv[0]/16,uv[3]/16},{uv[2]/16,uv[3]/16},{uv[2]/16,uv[1]/16}};Vertex[] v=new Vertex[4];
        for(int i=0;i<4;i++){double[] c=coords[Math.floorMod(i+rotation/90,4)];v[i]=new Vertex(p[i],c[0],c[1]);}
        return List.of(new Triangle(v[0],v[1],v[2],part,rgb,mask),new Triangle(v[0],v[2],v[3],part,rgb,mask));
    }
    public static List<Triangle> transform(List<Triangle> in,Matrix4dc matrix,int part,int color){return in.stream().map(t->t.transform(matrix,part,color)).toList();}
    public static List<Projection.Box> clip(List<Projection.Box> in,double x0,double y0,double z0,double x1,double y1,double z1,int color){
        List<Projection.Box> out=new ArrayList<>();for(var b:in){double ax=Math.max(x0,b.x0()),ay=Math.max(y0,b.y0()),az=Math.max(z0,b.z0()),bx=Math.min(x1,b.x1()),by=Math.min(y1,b.y1()),bz=Math.min(z1,b.z1());if(bx>ax&&by>ay&&bz>az)out.add(new Projection.Box(ax,ay,az,bx,by,bz,b.part(),color));}return out;
    }
}
