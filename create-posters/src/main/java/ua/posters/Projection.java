package ua.posters;
import java.util.*;
/** Hidden surfaces for both fast voxel shapes and true rotated model triangles. */
public final class Projection {
    public static final int RES=192,BYTES=RES*RES/8;
    public static final int JOINT=0x10,DETAIL=0x20,OUTLINE=0x30;
    public static final int[] MATERIALS={0,0x898989,0xad8d56,0x654a30,0x5b7a42,0xe5dfce,0x526e91,0x44484e,0xb76d45,0x98574b,0xd5bd7b,0x857493,0xa34040,0x242424,0xf7f5ed,0x8f785c};
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1,int part,int rgb){public Box(double x0,double y0,double z0,double x1,double y1,double z1,int part){this(x0,y0,z0,x1,y1,z1,part,0xad8d56);}}
    public record View(int direction,int width,int height,int resolution,double spanW,double spanH,byte[] pixels,boolean legacy){public static View legacy(int d,int w,int h,byte[] ink){return new View(d,w,h,RES,w,h,ink,true);}public byte[] ink(){return pixels;}}
    private record Face(double u0,double v0,double u1,double v1,double depth,int part,int material){}
    private record P(double u,double v,double d,double tu,double tv){}
    private record Tri(P a,P b,P c,Mesh.Triangle source,int material,double gu,double gv,int normal,double denominator){}
    public static boolean validResolution(int r){return r==384||r==512||r==768||r==1024;}
    public static int material(int rgb){int best=15;long nearest=Long.MAX_VALUE;for(int i=1;i<MATERIALS.length;i++){int c=MATERIALS[i],dr=((rgb>>16)&255)-((c>>16)&255),dg=((rgb>>8)&255)-((c>>8)&255),db=(rgb&255)-(c&255);long distance=3L*dr*dr+4L*dg*dg+2L*db*db;if(distance<nearest){best=i;nearest=distance;}}return best;}
    public static View project(List<Box> boxes,int sx,int sy,int sz,int direction){return project(boxes,sx,sy,sz,direction,384);}
    public static View project(List<Box> boxes,int sx,int sy,int sz,int direction,int resolution){return project(boxes,List.of(),sx,sy,sz,direction,resolution);}
    public static View project(List<Box> boxes,List<Mesh.Triangle> mesh,int sx,int sy,int sz,int direction,int resolution){
        if(direction<0||direction>5||!validResolution(resolution)||sx<1||sy<1||sz<1)throw new IllegalArgumentException("projection");
        int w=direction==1||direction==3?sz:sx,h=direction==0||direction==5?sz:sy;
        List<Face> faces=new ArrayList<>(boxes.size());List<Tri> triangles=new ArrayList<>(mesh.size()+1);triangles.add(null);
        double u0=Double.POSITIVE_INFINITY,v0=u0,u1=Double.NEGATIVE_INFINITY,v1=u1,far=u0,near=u1;Map<Integer,Integer> materials=new HashMap<>();
        for(Box b:boxes){int m=materials.computeIfAbsent(b.rgb,Projection::material);Face f=switch(direction){
            case 0->new Face(b.x0,b.z0,b.x1,b.z1,b.y1,b.part,m);case 1->new Face(sz-b.z1,sy-b.y1,sz-b.z0,sy-b.y0,b.x1,b.part,m);case 2->new Face(b.x0,sy-b.y1,b.x1,sy-b.y0,-b.z0,b.part,m);case 3->new Face(b.z0,sy-b.y1,b.z1,sy-b.y0,-b.x0,b.part,m);case 4->new Face(sx-b.x1,sy-b.y1,sx-b.x0,sy-b.y0,b.z1,b.part,m);default->new Face(b.x0,sz-b.z1,b.x1,sz-b.z0,-b.y0,b.part,m);};
            faces.add(f);u0=Math.min(u0,f.u0);v0=Math.min(v0,f.v0);u1=Math.max(u1,f.u1);v1=Math.max(v1,f.v1);far=Math.min(far,f.depth);near=Math.max(near,f.depth);
        }
        for(Mesh.Triangle source:mesh){
            P a=point(source.a(),direction,sx,sy,sz),b=point(source.b(),direction,sx,sy,sz),c=point(source.c(),direction,sx,sy,sz);
            double denominator=(b.u-a.u)*(c.v-a.v)-(c.u-a.u)*(b.v-a.v),gu=0,gv=0;
            if(Math.abs(denominator)>1e-12){gu=((b.d-a.d)*(c.v-a.v)-(c.d-a.d)*(b.v-a.v))/denominator;gv=((b.u-a.u)*(c.d-a.d)-(c.u-a.u)*(b.d-a.d))/denominator;}
            triangles.add(new Tri(a,b,c,source,materials.computeIfAbsent(source.rgb(),Projection::material),gu,gv,normal(gu,gv),denominator));
            for(P p:List.of(a,b,c)){u0=Math.min(u0,p.u);v0=Math.min(v0,p.v);u1=Math.max(u1,p.u);v1=Math.max(v1,p.v);far=Math.min(far,p.d);near=Math.max(near,p.d);}
        }
        if(faces.isEmpty()&&mesh.isEmpty())throw new IllegalArgumentException("empty");
        if(u1-u0<.00001){u0-=.015625;u1+=.015625;}if(v1-v0<.00001){v0-=.015625;v1+=.015625;}
        double spanW=u1-u0,spanH=v1-v0,scale=(resolution-12.0)/Math.max(spanW,spanH),ox=(resolution-spanW*scale)/2,oy=(resolution-spanH*scale)/2;
        float[] depth=new float[resolution*resolution];Arrays.fill(depth,Float.NEGATIVE_INFINITY);int[] parts=new int[depth.length];Arrays.fill(parts,-1);
        int[] surfaces=mesh.isEmpty()?null:new int[depth.length];byte[] paint=new byte[depth.length];long work=0;
        for(Face f:faces){
            int x0=clamp((int)Math.round(ox+(f.u0-u0)*scale),resolution),x1=clamp((int)Math.round(ox+(f.u1-u0)*scale),resolution),y0=clamp((int)Math.round(oy+(f.v0-v0)*scale),resolution),y1=clamp((int)Math.round(oy+(f.v1-v0)*scale),resolution);
            if(x1<=x0)x1=Math.min(resolution-1,x0+1);if(y1<=y0)y1=Math.min(resolution-1,y0+1);
            work+=(long)(x1-x0)*(y1-y0);if(work>100_000_000L)throw new IllegalArgumentException("complex");
            int tone=f.material|(shade(f.depth,near,far)<<6);
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){int i=x+y*resolution;if(closer(f.depth,f.part,depth[i],parts[i])){depth[i]=(float)f.depth;parts[i]=f.part;paint[i]=(byte)tone;if(surfaces!=null)surfaces[i]=0;}}
        }
        for(int ti=1;ti<triangles.size();ti++){
            Tri t=triangles.get(ti);int x0=clamp((int)Math.floor(ox+(Math.min(t.a.u,Math.min(t.b.u,t.c.u))-u0)*scale),resolution),x1=clamp((int)Math.ceil(ox+(Math.max(t.a.u,Math.max(t.b.u,t.c.u))-u0)*scale),resolution);
            int y0=clamp((int)Math.floor(oy+(Math.min(t.a.v,Math.min(t.b.v,t.c.v))-v0)*scale),resolution),y1=clamp((int)Math.ceil(oy+(Math.max(t.a.v,Math.max(t.b.v,t.c.v))-v0)*scale),resolution);
            if(Math.abs(t.denominator)<1e-12||x1-x0<1||y1-y0<1){
                P a=t.a,b=t.b;if(distance(a,t.c)>distance(a,b))b=t.c;if(distance(t.b,t.c)>distance(a,b)){a=t.b;b=t.c;}
                int steps=Math.max(1,(int)Math.ceil(distance(a,b)*scale));
                for(int k=0;k<=steps;k++){double f=k/(double)steps,d=a.d+(b.d-a.d)*f;int x=clamp((int)Math.round(ox+(a.u+(b.u-a.u)*f-u0)*scale),resolution),y=clamp((int)Math.round(oy+(a.v+(b.v-a.v)*f-v0)*scale),resolution),i=x+y*resolution;
                    if(closer(d,t.source.part(),depth[i],parts[i])){depth[i]=(float)d;parts[i]=t.source.part();surfaces[i]=ti;paint[i]=(byte)(t.material|(shade(d,near,far)<<6));}}
                continue;
            }
            work+=(long)(x1-x0+1)*(y1-y0+1);if(work>100_000_000L)throw new IllegalArgumentException("complex");
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
                double u=u0+(x+.5-ox)/scale,v=v0+(y+.5-oy)/scale;
                double b=((u-t.a.u)*(t.c.v-t.a.v)-(t.c.u-t.a.u)*(v-t.a.v))/t.denominator,c=((t.b.u-t.a.u)*(v-t.a.v)-(u-t.a.u)*(t.b.v-t.a.v))/t.denominator,a=1-b-c;
                if(a<-.0000001||b<-.0000001||c<-.0000001)continue;
                double d=a*t.a.d+b*t.b.d+c*t.c.d;int i=x+y*resolution;if(!closer(d,t.source.part(),depth[i],parts[i]))continue;
                if(t.source.mask()!=null&&!t.source.mask().visible(a*t.a.tu+b*t.b.tu+c*t.c.tu,a*t.a.tv+b*t.b.tv+c*t.c.tv))continue;
                depth[i]=(float)d;parts[i]=t.source.part();surfaces[i]=ti;paint[i]=(byte)(t.material|(shade(d,near,far)<<6));
            }
        }
        byte[] pixels=paint.clone();int flat=normal(0,0);
        for(int y=1;y<resolution-1;y++)for(int x=1;x<resolution-1;x++){
            int i=x+y*resolution;if(parts[i]<0)continue;int weight=0;
            Tri surface=surfaces==null||surfaces[i]==0?null:triangles.get(surfaces[i]);double gu=surface==null?0:surface.gu,gv=surface==null?0:surface.gv;int n=surface==null?flat:surface.normal;
            int nl=surfaces==null||surfaces[i-1]==0?flat:triangles.get(surfaces[i-1]).normal,nt=surfaces==null||surfaces[i-resolution]==0?flat:triangles.get(surfaces[i-resolution]).normal;
            if(parts[i-1]<0||parts[i+1]<0||parts[i-resolution]<0||parts[i+resolution]<0)weight=OUTLINE;
            else if(Math.abs(depth[i]-depth[i-1]-gu/scale)>.25||Math.abs(depth[i]-depth[i-resolution]-gv/scale)>.25||n!=nl||n!=nt||(paint[i]&15)!=(paint[i-1]&15)||(paint[i]&15)!=(paint[i-resolution]&15))weight=DETAIL;
            else if(scale>=5&&(parts[i]!=parts[i-1]||parts[i]!=parts[i-resolution]))weight=JOINT;
            pixels[i]=(byte)((paint[i]&255)|weight);
        }
        return new View(direction,w,h,resolution,spanW,spanH,pixels,false);
    }
    private static boolean closer(double d,int part,float old,int owner){return d>old+.0001||Math.abs(d-old)<=.0001&&part<owner;}
    private static int shade(double d,double near,double far){return near-far<.001?0:(int)Math.max(0,Math.min(3,3*(near-d)/(near-far)));}
    private static int normal(double gu,double gv){double length=Math.sqrt(gu*gu+gv*gv+1);return (int)Math.round((1-gu/length)*3.5)|((int)Math.round((1-gv/length)*3.5)<<3)|((int)Math.round((1+1/length)*3.5)<<6);}
    private static double distance(P a,P b){return Math.hypot(a.u-b.u,a.v-b.v);}
    private static P point(Mesh.Vertex v,int direction,int sx,int sy,int sz){var p=v.p();return switch(direction){case 0->new P(p.x(),p.z(),p.y(),v.u(),v.v());case 1->new P(sz-p.z(),sy-p.y(),p.x(),v.u(),v.v());case 2->new P(p.x(),sy-p.y(),-p.z(),v.u(),v.v());case 3->new P(p.z(),sy-p.y(),-p.x(),v.u(),v.v());case 4->new P(sx-p.x(),sy-p.y(),p.z(),v.u(),v.v());default->new P(p.x(),sz-p.z(),-p.y(),v.u(),v.v());};}
    private static int clamp(int n,int r){return Math.max(1,Math.min(r-1,n));}
    public static boolean ink(byte[] bits,int x,int y){int i=x+y*RES;return bits.length==BYTES&&(bits[i>>3]&(1<<(i&7)))!=0;}
}
