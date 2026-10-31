package ua.posters;
import java.util.*;
import net.minecraft.nbt.*;
public final class DrawingData {
    public static CompoundTag make(String title,int sx,int sy,int sz,List<Projection.View> views){return make(title,sx,sy,sz,views,PosterOptions.DEFAULT);}
    public static CompoundTag make(String title,int sx,int sy,int sz,List<Projection.View> views,PosterOptions options){
        CompoundTag tag=new CompoundTag();tag.m_128405_("Version",2);tag.m_128359_("Title",title);
        tag.m_128405_("X",sx);tag.m_128405_("Y",sy);tag.m_128405_("Z",sz);setOptions(tag,options);
        ListTag list=new ListTag();int total=0;
        for(var v:views){
            if(v.legacy()||v.resolution()!=options.resolution())throw new IllegalArgumentException("resolution");
            CompoundTag t=new CompoundTag();t.m_128405_("Direction",v.direction());t.m_128405_("Width",v.width());t.m_128405_("Height",v.height());
            t.m_128405_("Resolution",v.resolution());t.m_128347_("SpanW",v.spanW());t.m_128347_("SpanH",v.spanH());
            byte[] raster=RasterCodec.encode(v.pixels());total+=raster.length;if(total>RasterCodec.MAX_TOTAL)throw new IllegalArgumentException("complex");
            t.m_128382_("Raster",raster);list.add(t);
        }
        tag.m_128365_("Views",list);return tag;
    }
    public static void setOptions(CompoundTag t,PosterOptions o){t.m_128405_("PosterSize",o.size());t.m_128405_("Scale",o.scale());t.m_128379_("Muted",o.muted());}
    public static PosterOptions options(CompoundTag t){
        if(t.m_128451_("Version")!=2)return PosterOptions.DEFAULT;
        try{return new PosterOptions(t.m_128451_("PosterSize"),t.m_128451_("Scale"),t.m_128471_("Muted"));}catch(IllegalArgumentException ex){return PosterOptions.DEFAULT;}
    }
    /** Cheap metadata checks for each render frame; decompression is reserved for loading/texture generation. */
    public static boolean headerValid(CompoundTag tag){
        int version=tag.m_128451_("Version");if(version!=1&&version!=2||tag.m_128461_("Title").length()>64)return false;
        for(String axis:List.of("X","Y","Z")){int n=tag.m_128451_(axis);if(n<1||n>256)return false;}
        if(version==2){try{new PosterOptions(tag.m_128451_("PosterSize"),tag.m_128451_("Scale"),tag.m_128471_("Muted"));}catch(IllegalArgumentException ex){return false;}}
        ListTag list=tag.m_128437_("Views",10);if(list.isEmpty()||list.size()>6)return false;int seen=0,total=0;
        for(int i=0;i<list.size();i++){
            CompoundTag t=list.m_128728_(i);int d=t.m_128451_("Direction"),w=t.m_128451_("Width"),h=t.m_128451_("Height");
            if(d<0||d>5||(seen&(1<<d))!=0||w<1||h<1||w>256||h>256)return false;seen|=1<<d;
            if(version==1){if(t.m_128463_("Ink").length!=Projection.BYTES)return false;}
            else{
                int r=t.m_128451_("Resolution");double sw=t.m_128459_("SpanW"),sh=t.m_128459_("SpanH");byte[] data=t.m_128463_("Raster");total+=data.length;
                if(r!=options(tag).resolution()||!Double.isFinite(sw)||!Double.isFinite(sh)||sw<=0||sh<=0||sw>512||sh>512||data.length<1||data.length>RasterCodec.MAX_ENCODED)return false;
            }
        }
        return total<=RasterCodec.MAX_TOTAL;
    }
    public static List<Projection.View> views(CompoundTag tag){
        if(!headerValid(tag))return List.of();List<Projection.View> out=new ArrayList<>();ListTag list=tag.m_128437_("Views",10);boolean legacy=tag.m_128451_("Version")==1;
        try{for(int i=0;i<list.size();i++){
            CompoundTag t=list.m_128728_(i);int d=t.m_128451_("Direction"),w=t.m_128451_("Width"),h=t.m_128451_("Height");
            if(legacy)out.add(Projection.View.legacy(d,w,h,t.m_128463_("Ink")));
            else{int r=t.m_128451_("Resolution");out.add(new Projection.View(d,w,h,r,t.m_128459_("SpanW"),t.m_128459_("SpanH"),RasterCodec.decode(t.m_128463_("Raster"),r),false));}
        }}catch(IllegalArgumentException ex){return List.of();}
        return out;
    }
    public static boolean valid(CompoundTag tag){return !views(tag).isEmpty();}
    public static boolean fits(CompoundTag tag){var views=views(tag);return !views.isEmpty()&&(tag.m_128451_("Version")==1||Paper.layout(views,options(tag)).fits());}
    public static int textureWidth(CompoundTag tag){return tag.m_128451_("Version")==1?LegacyPaper.W:options(tag).textureWidth();}
    public static int textureHeight(CompoundTag tag){return tag.m_128451_("Version")==1?LegacyPaper.H:options(tag).textureHeight();}
    public static int[] pixels(CompoundTag tag){
        var views=views(tag);String title=tag.m_128461_("Title");int x=tag.m_128451_("X"),y=tag.m_128451_("Y"),z=tag.m_128451_("Z");
        if(tag.m_128451_("Version")==1)return LegacyPaper.compose(title,x,y,z,views);
        return Paper.compose(title,x,y,z,views,options(tag));
    }
}
