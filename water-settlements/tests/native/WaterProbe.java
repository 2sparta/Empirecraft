package com.quintonc.vs_sails.ship;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.*;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import ua.empirecraft.shipphysics.*;
@Mod("empirecraft_water_probe") public final class WaterProbe {
 MinecraftServer server;ServerLevel level;long boatId,raftId;int ticks,checks;double startY;LoadedServerShip boat,raft;
 void check(boolean good,String text){checks++;if(!good)throw new AssertionError(text);}
 public WaterProbe(){MinecraftForge.EVENT_BUS.addListener(this::start);MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void start(ServerStartedEvent e){server=e.getServer();level=server.overworld();PhysicsConfig.get().collisionProbe=false;var observer=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);observer.setPos(48,76,6);level.addNewPlayer(observer);try{var f=net.minecraft.server.players.PlayerList.class.getDeclaredField("f_11196_");f.setAccessible(true);((java.util.List)f.get(server.getPlayerList())).add(observer);}catch(Exception failure){throw new RuntimeException(failure);}}
 long build(int off,boolean walls){
  Set<BlockPos> blocks=new HashSet<>();
  for(int x=0;x<9;x++)for(int z=0;z<13;z++){
   var p=new BlockPos(off+x,64,z);level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),2);blocks.add(p);
   if(walls&&(x==0||z==0||x==8||z==12))for(int y=65;y<=67;y++){p=new BlockPos(off+x,y,z);level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),2);blocks.add(p);}
  }
  var s=ShipAssembler.assembleToShip(level,blocks,1);s.setStatic(walls);return s.getId();
 }
 void force(LoadedServerShip s)throws Exception{
  var a=s.getShipAABB();for(int x=a.minX()>>4;x<=(a.maxX()-1)>>4;x++)for(int z=a.minZ()>>4;z<=(a.maxZ()-1)>>4;z++)level.setChunkForced(x,z,true);
  if(s.getId()==raftId)s.setStatic(false);var control=SailsShipControl.getOrCreate(s,level);control.onServerTick();
  var f=ShipFlooding.class.getDeclaredField("LEVELS");f.setAccessible(true);var levels=(Map<?,?>)f.get(null);var c=(ShipFlooding.Context)((Map<?,?>)levels.get(level)).get(s.getId());c.forceUntil=100000;
 }
 void topology()throws Exception{
  var a=boat.getShipAABB();BlockPos centre=new BlockPos(a.minX()+4,a.minY()+1,a.minZ()+6),below=new BlockPos(a.minX()+4,a.minY()-1,a.minZ()+6);
  FloodState legacy=new FloodState();legacy.interior=new long[]{below.asLong()};var job=new FloodTopology.Job(boat,legacy);
  for(int n=0;n<300&&job.result==null&&!job.failed;n++)job.work(level,boat,2048);
  check(job.result!=null,"Bounded topology completes");Set<Long> inside=new HashSet<>();for(long p:job.result.interior())inside.add(p);
  check(inside.contains(centre.asLong()),"Supported open hull contains room");check(!inside.contains(below.asLong()),"Historical mask cannot seal air below keel");
  var region=ShipFlooding.region(level,boatId);check(region!=null&&region.contains(centre),"Scheduler publishes dry-space region");
  var outside=boat.getShipToWorld().transformPosition(new org.joml.Vector3d(below.getX()+.5,below.getY()+.5,below.getZ()+.5));
  check(!FloodZones.containsWorld(level,outside.x,outside.y,outside.z),"Below-hull ocean remains wet");
  var frame=WaterContact.sample(level,boat,List.of(new WaterContact.Voxel(new BlockPos(a.minX(),a.minY(),a.minZ()),1)));
  check(frame.points().size()>1,"Ordinary hull displaces enclosed air without buoys");
  var control=SailsShipControl.getOrCreate(boat,level);var f=SailsShipControl.class.getDeclaredField("waterFrame");f.setAccessible(true);check(f.get(control)!=null,"Bare wood uses hydrostatics");
  check(control.floodState.water.values().stream().mapToInt(Integer::intValue).sum()>0,"Submerged open deck floods from ocean");
  System.out.println("WATER_TOPOLOGY_PASS checks="+checks+" roomCells="+inside.size()+" water="+control.floodState.water.values().stream().mapToInt(Integer::intValue).sum());
 }
 void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||server==null)return;try{
  ticks++;if(ticks==2){boatId=build(0,true);raftId=build(48,false);}
  if(ticks>=15){boat=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(boatId);raft=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(raftId);if(boat!=null)force(boat);if(raft!=null)force(raft);}
  if(ticks==40){check(raft!=null&&boat!=null,"Actual VS ships loaded");startY=raft.getTransform().getPositionInWorld().y();System.out.println("WATER_RISE_START mass="+raft.getInertiaData().getMass()+" static="+raft.isStatic()+" position="+startY);}
  if(ticks==240)topology();
  if(ticks==440){double end=raft.getTransform().getPositionInWorld().y();check(Double.isFinite(end)&&end>startY+1,"No-buoy wood rises: "+startY+" -> "+end);check(end<72,"Raft remains below ocean surface while rising");System.out.println("WATER_NATIVE_PASS checks="+checks+" noBuoyRise="+startY+"->"+end+" staticFlood=true");server.halt(false);}
  if(ticks>500)throw new AssertionError("Deadline");
 }catch(Throwable t){System.out.println("WATER_NATIVE_FAIL "+t);t.printStackTrace();server.halt(false);}}
}
