package ua.empirecraft.townymap;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;

/** Bounded, immutable top-down pixels. ARGB on disk/wire; no paths or remote texture URLs. */
public final class ShipIcon {
    public static final int SIDE=64;
    public static final long REFRESH_MILLIS=24L*60*60*1000;
    public static boolean refreshDue(long now,long refreshed){return refreshed<=0||(now>=refreshed&&now-refreshed>=REFRESH_MILLIS);}
    private final int[] pixels;
    private final String key;
    public ShipIcon(int[] pixels) {
        if(pixels==null||pixels.length!=SIDE*SIDE)throw new IllegalArgumentException("Invalid ship image");
        this.pixels=pixels.clone();
        try {var bytes=ByteBuffer.allocate(pixels.length*4);for(int p:pixels)bytes.putInt(p);
            key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public int pixel(int x,int z){return pixels[z*SIDE+x];}
    public int[] pixels(){return pixels.clone();}
    public String key(){return key;}
    public void write(DataOutput out)throws IOException {out.writeInt(1);for(int p:pixels)out.writeInt(p);}
    public static ShipIcon read(DataInput in)throws IOException {
        if(in.readInt()!=1)throw new IOException("Unsupported ship image");
        int[] p=new int[SIDE*SIDE];for(int i=0;i<p.length;i++)p[i]=in.readInt();return new ShipIcon(p);
    }
    /** Scan one block at a time, so tall masts/water columns never escape the tick budget. */
    public static final class Projection {
        public interface Columns {
            int top(int x,int z); // Integer.MIN_VALUE means that a required chunk is not loaded; never load it.
            int color(int x,int y,int z); // zero means transparent, solid pixels must have alpha.
        }
        private final int minX,minY,minZ,maxX,maxY,maxZ;
        private final Columns source;
        private final int[] pixels=new int[SIDE*SIDE];
        private final Map<Long,Integer> columns=new HashMap<>();
        private int at,x,z,y;private boolean column,blocked;
        public Projection(int minX,int minY,int minZ,int maxX,int maxY,int maxZ,Columns source) {
            if(maxX<=minX||maxY<=minY||maxZ<=minZ||(long)maxX-minX>4096||(long)maxZ-minZ>4096||maxY-minY>4096)
                throw new IllegalArgumentException("Invalid ship bounds");
            this.minX=minX;this.minY=minY;this.minZ=minZ;this.maxX=maxX;this.maxY=maxY;this.maxZ=maxZ;this.source=source;
        }
        public int step(int budget) {
            int used=0;blocked=false;double side=Math.max(maxX-minX,maxZ-minZ),cx=((double)minX+maxX)/2,cz=((double)minZ+maxZ)/2;
            while(at<pixels.length&&used<budget) {
                if(!column){
                    x=(int)Math.floor(cx+((at%SIDE)-SIDE/2+.5)*side/(SIDE-4));
                    z=(int)Math.floor(cz+((at/SIDE)-SIDE/2+.5)*side/(SIDE-4));
                    if(x<minX||x>=maxX||z<minZ||z>=maxZ){at++;continue;}
                    long key=((long)x<<32)^(z&0xffffffffL);Integer known=columns.get(key);
                    if(known!=null){pixels[at++]=known;continue;}
                    y=source.top(x,z);used++;if(y==Integer.MIN_VALUE){blocked=true;break;}
                    y=Math.min(y,maxY-1);column=true;if(used>=budget)continue;
                }
                if(y<minY){finish(0);continue;}
                int color=source.color(x,y--,z);used++;
                if(color!=0)finish(color);
            }
            return used;
        }
        private void finish(int color){pixels[at++]=color;columns.put(((long)x<<32)^(z&0xffffffffL),color);column=false;}
        public boolean blocked(){return blocked;}
        public boolean complete(){return at==pixels.length;}
        public ShipIcon image(){
            if(!complete())throw new IllegalStateException("Ship image not ready");
            int[] outlined=pixels.clone();
            for(int z=1;z<SIDE-1;z++)for(int x=1;x<SIDE-1;x++)if(pixels[z*SIDE+x]==0&&
                    (pixels[z*SIDE+x-1]!=0||pixels[z*SIDE+x+1]!=0||pixels[(z-1)*SIDE+x]!=0||pixels[(z+1)*SIDE+x]!=0))outlined[z*SIDE+x]=0x990c1519;
            return new ShipIcon(outlined);
        }
    }
}
