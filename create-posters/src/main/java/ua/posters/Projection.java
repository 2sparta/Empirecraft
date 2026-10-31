package ua.posters;

import java.util.*;

/** Orthographic, hidden-surface projection. Pure Java; shared with the offline tests. */
public final class Projection {
    public static final int RES = 192, BYTES = RES * RES / 8;
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1,int part) {}
    public record View(int direction,int width,int height,byte[] ink) {}
    private record Face(double u0,double v0,double u1,double v1,double depth,int part) {}

    // TOP, RIGHT (+X), FRONT (-Z), LEFT (-X), BACK (+Z), BOTTOM.
    public static View project(List<Box> boxes,int sx,int sy,int sz,int direction) {
        if(direction<0 || direction>5) throw new IllegalArgumentException("direction");
        int w = direction==1 || direction==3 ? sz : sx;
        int h = direction==0 || direction==5 ? sz : sy;
        if(w<1 || h<1) throw new IllegalArgumentException("size");
        double scale = (RES-8.0)/Math.max(w,h), ox=(RES-w*scale)/2, oy=(RES-h*scale)/2;
        float[] depth=new float[RES*RES]; Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        int[] parts=new int[RES*RES]; Arrays.fill(parts,-1);
        long work=0;
        for(Box b: boxes) {
            Face f=switch(direction) {
                case 0 -> new Face(b.x0,b.z0,b.x1,b.z1,b.y1,b.part);
                case 1 -> new Face(sz-b.z1,sy-b.y1,sz-b.z0,sy-b.y0,b.x1,b.part);
                case 2 -> new Face(b.x0,sy-b.y1,b.x1,sy-b.y0,-b.z0,b.part);
                case 3 -> new Face(b.z0,sy-b.y1,b.z1,sy-b.y0,-b.x0,b.part);
                case 4 -> new Face(sx-b.x1,sy-b.y1,sx-b.x0,sy-b.y0,b.z1,b.part);
                default -> new Face(b.x0,sz-b.z1,b.x1,sz-b.z0,-b.y0,b.part);
            };
            int x0=clamp((int)Math.round(ox+f.u0*scale)), x1=clamp((int)Math.round(ox+f.u1*scale));
            int y0=clamp((int)Math.round(oy+f.v0*scale)), y1=clamp((int)Math.round(oy+f.v1*scale));
            if(x1<=x0 || y1<=y0) continue;
            work+=(long)(x1-x0)*(y1-y0);
            if(work>40_000_000) throw new IllegalArgumentException("complex");
            for(int y=y0;y<y1;y++) for(int x=x0;x<x1;x++) {
                int i=x+y*RES;
                if(f.depth>depth[i]+0.0001) {depth[i]=(float)f.depth;parts[i]=f.part;}
            }
        }
        byte[] ink=new byte[BYTES];
        for(int y=1;y<RES-1;y++) for(int x=1;x<RES-1;x++) {
            int i=x+y*RES;
            if(parts[i]<0) continue;
            // Exterior and depth changes are visible; coplanar block joints receive a fine line.
            if(parts[i-1]<0 || parts[i+1]<0 || parts[i-RES]<0 || parts[i+RES]<0 ||
               Math.abs(depth[i]-depth[i-1])>0.001 || Math.abs(depth[i]-depth[i-RES])>0.001 ||
               parts[i]!=parts[i-1] || parts[i]!=parts[i-RES]) ink[i>>3]|=(byte)(1<<(i&7));
        }
        return new View(direction,w,h,ink);
    }
    private static int clamp(int v){return Math.max(1,Math.min(RES-1,v));}
    public static boolean ink(byte[] bits,int x,int y){int i=x+y*RES;return bits.length==BYTES&&(bits[i>>3]&(1<<(i&7)))!=0;}
}
