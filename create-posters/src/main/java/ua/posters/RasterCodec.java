package ua.posters;
import java.io.ByteArrayOutputStream;
import java.util.zip.*;
/** Bounded zlib payloads keep detailed multi-view items below Minecraft's NBT/network limits. */
public final class RasterCodec {
    public static final int MAX_ENCODED=180_000,MAX_TOTAL=800_000;
    public static byte[] encode(byte[] raw){
        Deflater d=new Deflater(Deflater.BEST_SPEED);d.setInput(raw);d.finish();
        try{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];
            while(!d.finished()){int n=d.deflate(buffer);out.write(buffer,0,n);if(out.size()>MAX_ENCODED)throw new IllegalArgumentException("complex");}
            return out.toByteArray();
        }finally{d.end();}
    }
    public static byte[] decode(byte[] data,int resolution){
        if(data.length<1||data.length>MAX_ENCODED||!Projection.validResolution(resolution))throw new IllegalArgumentException("raster");
        Inflater in=new Inflater();in.setInput(data);
        try{byte[] raw=new byte[resolution*resolution];int offset=0;
            while(offset<raw.length&&!in.finished()){
                int n=in.inflate(raw,offset,raw.length-offset);if(n==0)throw new IllegalArgumentException("raster");offset+=n;
            }
            if(offset!=raw.length||!in.finished()||in.getRemaining()!=0)throw new IllegalArgumentException("raster");
            for(byte b:raw)if((b&15)==0&&b!=0)throw new IllegalArgumentException("raster");
            return raw;
        }catch(DataFormatException ex){throw new IllegalArgumentException("raster",ex);}finally{in.end();}
    }
}
