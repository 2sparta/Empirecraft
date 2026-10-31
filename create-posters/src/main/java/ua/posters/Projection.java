package ua.posters;
import java.util.*;
/** Orthographic hidden-surface projection with a material wash and three line weights. */
public final class Projection {
    public static final int RES=192,BYTES=RES*RES/8;
    public static final int JOINT=0x10,DETAIL=0x20,OUTLINE=0x30;
    public static final int[] MATERIALS={0,0x898989,0xad8d56,0x654a30,0x5b7a42,0xe5dfce,0x526e91,0x44484e,0xb76d45,0x98574b,0xd5bd7b,0x857493,0xa34040,0x242424,0xf7f5ed,0x8f785c};
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1,int part,int rgb) {
        public Box(double x0,double y0,double z0,double x1,double y1,double z1,int part){this(x0,y0,z0,x1,y1,z1,part,0xad8d56);}
    }
    public record View(int direction,int width,int height,int resolution,double spanW,double spanH,byte[] pixels,boolean legacy) {
        public static View legacy(int d,int w,int h,byte[] ink){return new View(d,w,h,RES,w,h,ink,true);}
        public byte[] ink(){return pixels;}
    }
    private record Face(double u0,double v0,double u1,double v1,double depth,int part,int material){}
    public static boolean validResolution(int r){return r==384||r==512||r==768||r==1024;}
    public static int material(int rgb){
        int best=15;long nearest=Long.MAX_VALUE;
        for(int i=1;i<MATERIALS.length;i++){
            int c=MATERIALS[i],dr=((rgb>>16)&255)-((c>>16)&255),dg=((rgb>>8)&255)-((c>>8)&255),db=(rgb&255)-(c&255);
            long distance=3L*dr*dr+4L*dg*dg+2L*db*db;if(distance<nearest){best=i;nearest=distance;}
        }
        return best;
    }
    public static View project(List<Box> boxes,int sx,int sy,int sz,int direction){return project(boxes,sx,sy,sz,direction,384);}
    // TOP, RIGHT (+X), FRONT (-Z), LEFT (-X), BACK (+Z), BOTTOM.
    public static View project(List<Box> boxes,int sx,int sy,int sz,int direction,int resolution){
        if(direction<0||direction>5||!validResolution(resolution)||sx<1||sy<1||sz<1)throw new IllegalArgumentException("projection");
        int w=direction==1||direction==3?sz:sx,h=direction==0||direction==5?sz:sy;
        List<Face> faces=new ArrayList<>(boxes.size());double u0=Double.POSITIVE_INFINITY,v0=u0,u1=Double.NEGATIVE_INFINITY,v1=u1;
        double far=Double.POSITIVE_INFINITY,near=Double.NEGATIVE_INFINITY;
        Map<Integer,Integer> materials=new HashMap<>();
        for(Box b:boxes){
            int m=materials.computeIfAbsent(b.rgb,Projection::material);
            Face f=switch(direction){
                case 0->new Face(b.x0,b.z0,b.x1,b.z1,b.y1,b.part,m);
                case 1->new Face(sz-b.z1,sy-b.y1,sz-b.z0,sy-b.y0,b.x1,b.part,m);
                case 2->new Face(b.x0,sy-b.y1,b.x1,sy-b.y0,-b.z0,b.part,m);
                case 3->new Face(b.z0,sy-b.y1,b.z1,sy-b.y0,-b.x0,b.part,m);
                case 4->new Face(sx-b.x1,sy-b.y1,sx-b.x0,sy-b.y0,b.z1,b.part,m);
                default->new Face(b.x0,sz-b.z1,b.x1,sz-b.z0,-b.y0,b.part,m);
            };
            faces.add(f);u0=Math.min(u0,f.u0);v0=Math.min(v0,f.v0);u1=Math.max(u1,f.u1);v1=Math.max(v1,f.v1);far=Math.min(far,f.depth);near=Math.max(near,f.depth);
        }
        if(faces.isEmpty()||u1<=u0||v1<=v0)throw new IllegalArgumentException("empty");
        double spanW=u1-u0,spanH=v1-v0,scale=(resolution-12.0)/Math.max(spanW,spanH);
        double ox=(resolution-spanW*scale)/2,oy=(resolution-spanH*scale)/2;
        float[] depth=new float[resolution*resolution];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        int[] parts=new int[depth.length];Arrays.fill(parts,-1);byte[] paint=new byte[depth.length];long work=0;
        for(Face f:faces){
            int x0=clamp((int)Math.round(ox+(f.u0-u0)*scale),resolution),x1=clamp((int)Math.round(ox+(f.u1-u0)*scale),resolution);
            int y0=clamp((int)Math.round(oy+(f.v0-v0)*scale),resolution),y1=clamp((int)Math.round(oy+(f.v1-v0)*scale),resolution);
            if(x1<=x0)x1=Math.min(resolution-1,x0+1);if(y1<=y0)y1=Math.min(resolution-1,y0+1);
            work+=(long)(x1-x0)*(y1-y0);if(work>100_000_000L)throw new IllegalArgumentException("complex");
            int shade=near-far<.001?0:(int)Math.min(3,3*(near-f.depth)/(near-far));
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
                int i=x+y*resolution;
                if(f.depth>depth[i]+.0001||(Math.abs(f.depth-depth[i])<=.0001&&f.part<parts[i])){
                    depth[i]=(float)f.depth;parts[i]=f.part;paint[i]=(byte)(f.material|(shade<<6));
                }
            }
        }
        byte[] pixels=paint.clone();
        for(int y=1;y<resolution-1;y++)for(int x=1;x<resolution-1;x++){
            int i=x+y*resolution;if(parts[i]<0)continue;int weight=0;
            if(parts[i-1]<0||parts[i+1]<0||parts[i-resolution]<0||parts[i+resolution]<0)weight=OUTLINE;
            else if(Math.abs(depth[i]-depth[i-1])>.25||Math.abs(depth[i]-depth[i-resolution])>.25||(paint[i]&15)!=(paint[i-1]&15)||(paint[i]&15)!=(paint[i-resolution]&15))weight=DETAIL;
            else if(scale>=5&&(parts[i]!=parts[i-1]||parts[i]!=parts[i-resolution]))weight=JOINT;
            pixels[i]=(byte)((paint[i]&255)|weight);
        }
        return new View(direction,w,h,resolution,spanW,spanH,pixels,false);
    }
    private static int clamp(int n,int r){return Math.max(1,Math.min(r-1,n));}
    public static boolean ink(byte[] bits,int x,int y){int i=x+y*RES;return bits.length==BYTES&&(bits[i>>3]&(1<<(i&7)))!=0;}
}
