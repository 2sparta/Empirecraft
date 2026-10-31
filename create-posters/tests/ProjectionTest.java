package ua.posters;
import java.util.*;import java.nio.file.*;import java.io.*;
import net.minecraft.nbt.*;import net.minecraft.network.FriendlyByteBuf;import io.netty.buffer.Unpooled;
public final class ProjectionTest {
    static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static long lines(Projection.View v,int type){long n=0;for(byte b:v.pixels())if((b&15)!=0&&(b&0x30)==type)n++;return n;}
    static CompoundTag legacy(){
        CompoundTag t=new CompoundTag();t.m_128405_("Version",1);t.m_128359_("Title","Старий плакат");for(String a:List.of("X","Y","Z"))t.m_128405_(a,4);
        CompoundTag v=new CompoundTag();v.m_128405_("Direction",2);v.m_128405_("Width",4);v.m_128405_("Height",4);byte[] ink=new byte[Projection.BYTES];Arrays.fill(ink,(byte)0x11);v.m_128382_("Ink",ink);ListTag vs=new ListTag();vs.add(v);t.m_128365_("Views",vs);return t;
    }
    static void mustReject(Runnable r,String why){boolean failed=false;try{r.run();}catch(IllegalArgumentException ex){failed=true;}check(failed,why);}
    public static void main(String[] args)throws Exception{
        long started=System.nanoTime();Path dir=Path.of(args[0]);
        var cube=new Projection.Box(0,0,0,4,4,4,0,0xad8d56);var hidden=new Projection.Box(1,1,1,2,2,2,1,0xa34040);
        for(int d=0;d<6;d++){
            var a=Projection.project(List.of(cube),4,4,4,d);var b=Projection.project(List.of(hidden,cube),4,4,4,d);
            check(Arrays.equals(a.pixels(),b.pixels()),"hidden geometry and colour view "+d);check(lines(a,Projection.OUTLINE)>1000,"exterior contour "+d);
        }
        var top=Projection.project(List.of(new Projection.Box(4,10,3,7,18,8,0)),20,30,20,0);
        var right=Projection.project(List.of(new Projection.Box(4,10,3,7,18,8,0)),20,30,20,1);
        check(top.spanW()==3&&top.spanH()==5,"empty schematic margins removed");check(right.spanW()==5&&right.spanH()==8,"right view axes preserved");
        List<Projection.Box> tiles=new ArrayList<>();for(int x=0;x<256;x++)tiles.add(new Projection.Box(x,0,0,x+1,64,1,x));
        var sparse=Projection.project(tiles,256,64,1,2);check(lines(sparse,Projection.JOINT)==0,"dense grid suppressed at small scale");
        var fine=Projection.project(tiles.subList(0,30),30,64,1,2,1024);check(lines(fine,Projection.JOINT)>0,"fine joints retained when legible");
        check(Projection.material(0xad8d56)==2&&Projection.material(0x654a30)==3,"material colours");
        mustReject(()->new PosterOptions(4,0,true),"invalid size");mustReject(()->new PosterOptions(0,7,true),"invalid scale");
        var geometry=ShipFixture.boxes();List<Projection.View> views=new ArrayList<>();for(int d=0;d<6;d++)views.add(Projection.project(geometry,37,77,91,d));
        for(int size=0;size<4;size++){
            var o=new PosterOptions(size,0,true);check(o.width()==3*(size+1)&&o.height()==2*(size+1),"wall dimensions "+size);check(o.paper()==4*(size+1)*(size+1)&&o.ink()==size+1,"material cost "+size);
            check(o.textureWidth()>=1536&&o.textureHeight()*3==o.textureWidth()*2,"texture aspect "+size);
        }
        var shared=Paper.layout(views.subList(0,3),PosterOptions.DEFAULT);double ppu=shared.panels().get(0).pixelsPerBlock();
        check(shared.panels().stream().allMatch(p->Math.abs(p.pixelsPerBlock()-ppu)<1e-9),"shared projection scale");
        check(shared.panels().get(1).width()>shared.panels().get(0).width(),"widest elevation gets larger panel");
        var each=Paper.layout(views.subList(0,3),new PosterOptions(0,1,true));check(each.panels().stream().map(Paper.Panel::pixelsPerBlock).distinct().count()>1,"separate fitting option");
        check(!Paper.layout(views.subList(0,3),new PosterOptions(0,2,true)).fits(),"oversize fixed scale blocked");
        var fixedOptions=new PosterOptions(3,3,true);var fixed=Paper.layout(views.subList(0,3),fixedOptions);
        check(fixed.fits(),"larger sheet fits 1:32");
        for(var panel:fixed.panels())check(Math.abs(panel.pixelsPerBlock()/Paper.BASE_W*fixedOptions.width()-1.0/32)<1e-9,"true physical 1:32 scale");
        CompoundTag tag=DrawingData.make("Двощогловий корабель",37,77,91,views.subList(0,3));check(DrawingData.valid(tag)&&DrawingData.fits(tag),"v2 drawing valid");
        FriendlyByteBuf wire=new FriendlyByteBuf(Unpooled.buffer());wire.m_130079_(tag);CompoundTag saved=wire.m_130260_();wire.release();check(tag.equals(saved),"coloured drawing NBT roundtrip");
        check(Arrays.equals(DrawingData.pixels(tag),DrawingData.pixels(saved)),"appearance after save/load");
        CompoundTag bad=tag.m_6426_();bad.m_128437_("Views",10).m_128728_(0).m_128382_("Raster",new byte[]{1,2,3});check(!DrawingData.valid(bad),"corrupt compressed raster rejected");
        bad=tag.m_6426_();bad.m_128405_("PosterSize",3);check(!DrawingData.valid(bad),"size and resolution mismatch rejected");
        bad=tag.m_6426_();bad.m_128437_("Views",10).add(bad.m_128437_("Views",10).m_128728_(0).m_6426_());check(!DrawingData.valid(bad),"duplicate views rejected");
        byte[] raster=views.get(0).pixels(),compressed=RasterCodec.encode(raster);check(Arrays.equals(raster,RasterCodec.decode(compressed,384)),"lossless material and edge compression");
        mustReject(()->RasterCodec.decode(Arrays.copyOf(compressed,compressed.length-1),384),"truncated compressed data");
        mustReject(()->RasterCodec.decode(Arrays.copyOf(compressed,compressed.length+1),384),"trailing compressed data");
        mustReject(()->RasterCodec.decode(RasterCodec.encode(new byte[384*384+1]),384),"decompression expansion bounded");
        CompoundTag old=legacy();check(DrawingData.valid(old),"v1 item compatibility");check(DrawingData.options(old).width()==3&&DrawingData.textureWidth(old)==768,"v1 dimensions preserved");check(DrawingData.pixels(old).length==768*512,"v1 paper renderer available");
        var req=new PosterNetwork.Request(5,17,7,"Брама",true,3,3,true);wire=new FriendlyByteBuf(Unpooled.buffer());req.encode(wire);check(req.equals(PosterNetwork.Request.decode(wire)),"new settings request codec");wire.release();
        var result=new PosterNetwork.Result(5,17,"",false,tag);wire=new FriendlyByteBuf(Unpooled.buffer());result.encode(wire);check(result.equals(PosterNetwork.Result.decode(wire)),"response codec");wire.release();
        for(int n=1;n<=6;n++){
            var o=PosterOptions.DEFAULT;int[] pixels=Paper.compose("Двощогловий корабель",37,77,91,views.subList(0,n),o);check(pixels.length==o.textureWidth()*o.textureHeight(),"layout "+n);
            if(n==3||n==6)write(dir.resolve("preview-"+n+".ppm"),pixels,o.textureWidth(),o.textureHeight());
        }
        int[] muted=DrawingData.pixels(tag),graphite=Paper.compose("Двощогловий корабель",37,77,91,views.subList(0,3),new PosterOptions(0,0,false));
        check(!Arrays.equals(muted,graphite),"optional faded material washes");write(dir.resolve("preview-graphite.ppm"),graphite,1536,1024);
        List<Projection.View> large=new ArrayList<>();for(int d=0;d<6;d++)large.add(Projection.project(geometry,37,77,91,d,1024));
        CompoundTag big=DrawingData.make("Двощогловий корабель",37,77,91,large,new PosterOptions(3,0,true));check(DrawingData.valid(big),"12x8 six-view high resolution drawing");
        wire=new FriendlyByteBuf(Unpooled.buffer());wire.m_130079_(big);check(wire.readableBytes()<900_000,"largest item stays below packet budget");saved=wire.m_130260_();wire.release();check(big.equals(saved),"largest drawing fits real Minecraft NBT quota");
        CompoundTag physical=DrawingData.make("Двощогловий корабель",37,77,91,large.subList(0,3),fixedOptions);check(DrawingData.fits(physical),"12x8 fixed scale saved");
        write(dir.resolve("preview-large.ppm"),DrawingData.pixels(physical),fixedOptions.textureWidth(),fixedOptions.textureHeight());
        check(Arrays.equals(views.get(2).pixels(),Projection.project(geometry,37,77,91,2).pixels()),"deterministic projection");
        System.out.println("PASS "+checks+" checks: contours, colours, sizes, true scale, legacy items, compression, NBT and protocol ("+((System.nanoTime()-started)/1_000_000)+" ms)");
    }
    static void write(Path file,int[] rgb,int w,int h)throws Exception{try(var out=new BufferedOutputStream(Files.newOutputStream(file))){out.write(("P6\n"+w+" "+h+"\n255\n").getBytes());for(int p:rgb){out.write((p>>16)&255);out.write((p>>8)&255);out.write(p&255);}}}
}
