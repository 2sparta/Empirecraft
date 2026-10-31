package ua.posters;
import java.util.*;import java.nio.file.*;import java.io.*;
import net.minecraft.nbt.*;import net.minecraft.network.FriendlyByteBuf;import io.netty.buffer.Unpooled;
public final class ProjectionTest{
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static int ink(byte[] a){int n=0;for(byte b:a)n+=Integer.bitCount(b&255);return n;}
 public static void main(String[] args)throws Exception{
  var cube=new Projection.Box(0,0,0,4,4,4,0);var hidden=new Projection.Box(1,1,1,2,2,2,1);
  for(int d=0;d<6;d++){
   var a=Projection.project(List.of(cube),4,4,4,d);var b=Projection.project(List.of(cube,hidden),4,4,4,d);
   check(Arrays.equals(a.ink(),b.ink()),"hidden geometry view "+d);check(ink(a.ink())>500,"visible perimeter "+d);
  }
  var top=Projection.project(List.of(new Projection.Box(0,0,0,3,8,5,0)),3,8,5,0);
  var right=Projection.project(List.of(new Projection.Box(0,0,0,3,8,5,0)),3,8,5,1);
  check(top.width()==3&&top.height()==5,"top axes");check(right.width()==5&&right.height()==8,"right axes");
  var thin=Projection.project(List.of(new Projection.Box(0,0,0,1,.5,1,0)),1,1,1,2);
  check(!Projection.ink(thin.ink(),4,4)&&Projection.ink(thin.ink(),4,110),"slab silhouette");
  boolean rejected=false;try{Projection.project(List.of(cube),4,4,4,6);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid view");
  List<Projection.Box> boxes=new ArrayList<>();int id=0;
  // A small gatehouse: real unit-block geometry, a doorway, windows and stepped gables.
  for(int y=0;y<6;y++)for(int x=0;x<11;x++)for(int z=0;z<9;z++){
   boolean outer=x==0||x==10||z==0||z==8;
   boolean doorway=z==0&&x>=4&&x<=6&&y<3;
   boolean window=y>=2&&y<=3&&((z==0&&(x==2||x==8))||(x==10&&z>=3&&z<=5));
   if(y==0||outer&&!doorway&&!window)boxes.add(new Projection.Box(x,y,z,x+1,y+1,z+1,id++));
  }
  for(int y=0;y<4;y++)for(int x=y;x<11-y;x++)for(int z=-1;z<10;z++){
   if(x==y||x==10-y||z==-1||z==9)boxes.add(new Projection.Box(x,6+y,z+1,x+1,6.5+y,z+2,id++));
  }
  List<Projection.View> views=new ArrayList<>();for(int d=0;d<6;d++)views.add(Projection.project(boxes,11,10,11,d));
  CompoundTag tag=DrawingData.make("Брама майстерні",11,10,11,views.subList(0,3));check(DrawingData.valid(tag),"valid drawing");
  FriendlyByteBuf wire=new FriendlyByteBuf(Unpooled.buffer());wire.m_130079_(tag);CompoundTag decoded=wire.m_130260_();wire.release();
  check(tag.equals(decoded),"NBT network roundtrip");check(Arrays.equals(DrawingData.pixels(tag),DrawingData.pixels(decoded)),"saved poster identical");
  CompoundTag bad=tag.m_6426_();bad.m_128437_("Views",10).m_128728_(0).m_128382_("Ink",new byte[1]);check(!DrawingData.valid(bad),"invalid pixel data rejected");
  CompoundTag dup=tag.m_6426_();dup.m_128437_("Views",10).add(dup.m_128437_("Views",10).m_128728_(0).m_6426_());check(!DrawingData.valid(dup),"duplicate views rejected");
  var req=new PosterNetwork.Request(5,17,7,"Брама",true);wire=new FriendlyByteBuf(Unpooled.buffer());req.encode(wire);check(req.equals(PosterNetwork.Request.decode(wire)),"request codec");wire.release();
  var result=new PosterNetwork.Result(5,17,"",false,tag);wire=new FriendlyByteBuf(Unpooled.buffer());result.encode(wire);check(result.equals(PosterNetwork.Result.decode(wire)),"response codec");wire.release();
  for(int n=1;n<=6;n++){int[] pix=Paper.compose("Брама майстерні",11,10,11,views.subList(0,n));check(pix.length==768*512,"layout "+n);write(Path.of(args[0],"preview-"+n+".ppm"),pix);}
  // Repeated projection determinism is important for server caching and saved item copies.
  check(Arrays.equals(views.get(2).ink(),Projection.project(boxes,11,10,11,2).ink()),"deterministic projection");
  System.out.println("PASS "+checks+" geometry, occlusion, six layouts, NBT and protocol checks");
 }
 static void write(Path file,int[] rgb)throws Exception{try(var out=new BufferedOutputStream(Files.newOutputStream(file))){out.write("P6\n768 512\n255\n".getBytes());for(int p:rgb){out.write((p>>16)&255);out.write((p>>8)&255);out.write(p&255);}}}
}
