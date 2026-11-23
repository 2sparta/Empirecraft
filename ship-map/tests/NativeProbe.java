package ua.empirecraft.shipmapprobe;
import java.util.*;
import java.lang.reflect.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.core.api.ships.*;
import org.valkyrienskies.mod.common.*;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import ua.empirecraft.townymap.*;
@Mod("empirecraft_ship_map_probe")public final class NativeProbe {
 MinecraftServer server;ServerLevel level;ServerPlayer observer;long id;int ticks,checks;LoadedServerShip ship;Object atlas;Path directory;ShipIcon portrait;
 Object invoke(Object object,String name,Class<?>[] args,Object... values)throws Exception{var m=object.getClass().getDeclaredMethod(name,args);m.setAccessible(true);return m.invoke(object,values);}
 Object timed(Path path,java.util.function.LongSupplier time)throws Exception{var c=Class.forName("ua.empirecraft.townymap.ShipIconAtlas").getDeclaredConstructor(Path.class,java.util.function.LongSupplier.class);c.setAccessible(true);return c.newInstance(path,time);}
 void bake(Object object)throws Exception{for(int i=0;i<45;i++)invoke(object,"tick",new Class[]{MinecraftServer.class},server);}
 Object atlas(Path path)throws Exception{var c=Class.forName("ua.empirecraft.townymap.ShipIconAtlas").getDeclaredConstructor(Path.class);c.setAccessible(true);return c.newInstance(path);}
 void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 Long target()throws Exception{var m=Class.forName("ua.empirecraft.townymap.SpyglassService").getDeclaredMethod("target",ServerPlayer.class);m.setAccessible(true);return (Long)m.invoke(null,observer);}
 public NativeProbe(){MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event)->{try{
  server=event.getServer();level=server.overworld();observer=FakePlayerFactory.getMinecraft(level);observer.setPos(6.5,104,-12);
  var field=net.minecraft.network.Connection.class.getDeclaredField("f_129468_");field.setAccessible(true);var transport=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("f_9742_");transport.setAccessible(true);field.set(transport.get(observer.connection),new io.netty.channel.embedded.EmbeddedChannel());
  level.addNewPlayer(observer);var f=net.minecraft.server.players.PlayerList.class.getDeclaredField("f_11196_");f.setAccessible(true);((List)f.get(server.getPlayerList())).add(observer);
  directory=server.getWorldPath(LevelResource.ROOT).resolve("ship-map-probe-icons");atlas=atlas(directory);
 }catch(Throwable e){fail(e);}});MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void fail(Throwable error){System.out.println("SHIP_MAP_NATIVE_FAIL "+error);error.printStackTrace();if(server!=null)server.halt(false);}
 void tick(TickEvent.ServerTickEvent e){if(server==null||e.phase!=TickEvent.Phase.END)return;try{ticks++;
  if(ticks==2){Set<BlockPos> blocks=new HashSet<>();for(int x=0;x<13;x++)for(int z=0;z<20;z++)if(x<9||z>12){var p=new BlockPos(x,100,z);level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),2);blocks.add(p);}
   for(int y=101;y<108;y++){var p=new BlockPos(6,y,5);level.setBlock(p,Blocks.OAK_FENCE.defaultBlockState(),2);blocks.add(p);}
   for(int x=1;x<5;x++)for(int z=12;z<17;z++){var p=new BlockPos(x,101,z);level.setBlock(p,Blocks.RED_WOOL.defaultBlockState(),2);blocks.add(p);}
   var hull=ShipAssembler.assembleToShip(level,blocks,1);hull.setStatic(true);id=hull.getId();}
  if(ticks==20){ship=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);check(ship!=null,"Real loaded VS hull");var a=ship.getShipAABB();for(int x=a.minX()>>4;x<=(a.maxX()-1)>>4;x++)for(int z=a.minZ()>>4;z<=(a.maxZ()-1)>>4;z++)level.setChunkForced(x,z,true);}
  invoke(atlas,"tick",new Class[]{MinecraftServer.class},server);
  if(ticks==60){portrait=(ShipIcon)invoke(atlas,"image",new Class[]{long.class},id);check(portrait!=null,"Creation portrait is generated on the actual server");
   int opaque=0,wood=0,red=0;int brown=0xff000000|Blocks.OAK_PLANKS.defaultBlockState().getMapColor(level,BlockPos.ZERO).col,r=0xff000000|Blocks.RED_WOOL.defaultBlockState().getMapColor(level,BlockPos.ZERO).col;
   for(int pixel:portrait.pixels()){if((pixel>>>24)!=0)opaque++;if(pixel==brown)wood++;if(pixel==r)red++;}
   check(opaque>300&&opaque<4096,"Portrait has a transparent hull silhouette");check(wood>100&&red>5,"Top-down pixels use the actual deck and cabin colors");
   var measuring=atlas(directory.resolve("bounded"));int before=level.getChunkSource().getLoadedChunksCount();long start=System.nanoTime();for(int n=0;n<45;n++)invoke(measuring,"tick",new Class[]{MinecraftServer.class},server);double ms=(System.nanoTime()-start)/1e6;
   check(invoke(measuring,"image",new Class[]{long.class},id)!=null,"Bounded scanner finishes");check(before==level.getChunkSource().getLoadedChunksCount(),"Portrait generation does not load/generate chunks");((AutoCloseable)measuring).close();System.out.println("SHIP_MAP_NATIVE_SCAN milliseconds="+ms+" chunksUnchanged=true");
   observer.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SPYGLASS));observer.startUsingItem(InteractionHand.MAIN_HAND);var a=ship.getShipAABB();observer.lookAt(Anchor.EYES,ShipSight.worldPoint(ship,new Vec3(a.minX()+6.5,a.minY()+3.5,a.minZ()+5.5)));
   check(Objects.equals(target(),id),"Actual server scope ray selects the moving-ship coordinate frame");
   for(int x=4;x<9;x++)for(int y=100;y<109;y++)level.setBlock(new BlockPos(x,y,-2),Blocks.STONE.defaultBlockState(),2);
   check(target()==null,"World terrain blocks optical reconnaissance");
   for(int x=4;x<9;x++)for(int y=100;y<109;y++)level.setBlock(new BlockPos(x,y,-2),Blocks.AIR.defaultBlockState(),2);
   check(Objects.equals(target(),id),"Removing the occluder restores the contact");observer.stopUsingItem();check(target()==null,"No optical target without active spyglass use");
   observer.updateOptions(new net.minecraft.network.protocol.game.ServerboundClientInformationPacket("en_us",2,net.minecraft.world.entity.player.ChatVisiblity.FULL,true,127,net.minecraft.world.entity.HumanoidArm.RIGHT,false,true));
   check(ShipViewDistance.blocks(observer)==32,"Vanilla client settings define the optical render distance");
   observer.updateOptions(new net.minecraft.network.protocol.game.ServerboundClientInformationPacket("en_us",1000,net.minecraft.world.entity.player.ChatVisiblity.FULL,true,127,net.minecraft.world.entity.HumanoidArm.RIGHT,false,true));
   check(ShipViewDistance.blocks(observer)<=16*server.getPlayerList().getViewDistance(),"Client render distance cannot exceed the server cap");
   var a2=ship.getShipAABB();level.setBlock(new BlockPos(a2.minX()+2,a2.minY()+2,a2.minZ()+14),Blocks.GOLD_BLOCK.defaultBlockState(),2);
   check(portrait.key().equals(((ShipIcon)invoke(atlas,"image",new Class[]{long.class},id)).key()),"Creation portrait is cached rather than rescanned every tick");
   ((AutoCloseable)atlas).close();atlas=null;var restored=atlas(directory);check(portrait.key().equals(((ShipIcon)invoke(restored,"image",new Class[]{long.class},id)).key()),"Creation portrait survives server restart");((AutoCloseable)restored).close();
   var now=new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());Path daily=directory.resolve("daily");var timed=timed(daily,now::get);bake(timed);var initial=(ShipIcon)invoke(timed,"image",new Class[]{long.class},id);
   for(int x=0;x<13;x++)for(int z=0;z<20;z++)level.setBlock(new BlockPos(a2.minX()+x,a2.minY()+2,a2.minZ()+z),Blocks.BLUE_WOOL.defaultBlockState(),2);
   now.addAndGet(ShipIcon.REFRESH_MILLIS-1);bake(timed);check(initial.key().equals(((ShipIcon)invoke(timed,"image",new Class[]{long.class},id)).key()),"Hull changes do not refresh a portrait before 24 real hours");((AutoCloseable)timed).close();
   timed=timed(daily,now::get);check(initial.key().equals(((ShipIcon)invoke(timed,"image",new Class[]{long.class},id)).key()),"Restart preserves the previous refresh timestamp");now.incrementAndGet();bake(timed);var updated=(ShipIcon)invoke(timed,"image",new Class[]{long.class},id);check(!updated.key().equals(initial.key()),"The portrait refreshes when exactly 24 hours have elapsed");((AutoCloseable)timed).close();
   for(int x=0;x<13;x++)for(int z=0;z<20;z++)level.setBlock(new BlockPos(a2.minX()+x,a2.minY()+2,a2.minZ()+z),Blocks.RED_WOOL.defaultBlockState(),2);
   timed=timed(daily,now::get);bake(timed);check(updated.key().equals(((ShipIcon)invoke(timed,"image",new Class[]{long.class},id)).key()),"Restart does not trigger a second daily refresh");((AutoCloseable)timed).close();
   System.out.println("SHIP_MAP_NATIVE_PASS checks="+checks+" realHull=true colors=true occlusion=true loadedOnly=true persistence=true dailyRefresh=true");server.halt(false);
  }
 }catch(Throwable error){fail(error);}}
}
