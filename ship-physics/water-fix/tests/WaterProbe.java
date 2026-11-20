package ua.empirecraft.shipphysics.test;
import java.util.*;
import java.lang.reflect.*;
import com.quintonc.vs_sails.registration.SailsBlocks;
import com.quintonc.vs_sails.ship.*;
import com.quintonc.vs_sails.compat.RiggingEvents;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.*;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import ua.empirecraft.shipphysics.*;
@Mod("empirecraft_water_probe") public class WaterProbe {
 MinecraftServer server;ServerLevel level;long id;int ticks,checks;double baseline;BlockPos wet;LoadedServerShip ship;
 public WaterProbe(){MinecraftForge.EVENT_BUS.addListener(this::start);MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void check(boolean value,String msg){checks++;if(!value)throw new AssertionError(msg);}
 void start(ServerStartedEvent e){server=e.getServer();level=server.overworld();PhysicsConfig.get().collisionProbe=false;}
 void build(){
  Set<BlockPos> blocks=new HashSet<>();
  for(int x=0;x<12;x++)for(int z=0;z<20;z++){
   var p=new BlockPos(x,100,z);level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),2);blocks.add(p);
   if(x==0||z==0||x==11||z==19)for(int y=101;y<=103;y++){p=new BlockPos(x,y,z);level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),2);blocks.add(p);}
  }
  var mast=new BlockPos(4,104,10);level.setBlock(mast,SailsBlocks.SAIL_BLOCK.get().defaultBlockState(),2);blocks.add(mast);
  var assembled=ShipAssembler.assembleToShip(level,blocks,1);id=assembled.getId();assembled.setStatic(true);
 }
 Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 void tests()throws Exception{
  check(!MassSync.busy(id),"Initial bounded migration complete");baseline=ship.getInertiaData().getMass();
  check(Double.isFinite(baseline)&&baseline>0,"Finite hull mass");
  var a=ship.getShipAABB();wet=new BlockPos(a.minX()+3,a.minY()+1,a.minZ()+3);
  var air=BlockStateInfo.INSTANCE.get(Blocks.AIR.defaultBlockState());
  for(int amount=0;amount<16;amount++){
   var state=Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,amount);var info=BlockStateInfo.INSTANCE.get(state);
   check(info.getFirst()==0,"Native water mass is zero at level "+amount);
   check(!info.getSecond().equals(air.getSecond()),"Native fluid voxel retained at level "+amount);
   level.setBlock(wet,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
   check(Math.abs(ship.getInertiaData().getMass()-baseline)<1e-4,"Water state does not change dry hull inertia "+amount);
  }
  level.setBlock(wet,Blocks.AIR.defaultBlockState(),2);
  check(Math.abs(ship.getInertiaData().getMass()-baseline)<1e-4,"Removing water retains dry mass");
  var control=SailsShipControl.getOrCreate(ship,level);control.onServerTick();
  check(ShipFlooding.pour(level,wet,1),"Finite water added");
  var load=(ShipFlooding.Load)field(control,"floodLoad");check(load.mass()==125,"One eighth bucket weighs 125kg once");
  ShipFlooding.pour(level,wet,1);load=(ShipFlooding.Load)field(control,"floodLoad");check(load.mass()==250,"Two eighths weigh 250kg once");
  check(Math.abs(ship.getInertiaData().getMass()-baseline)<1e-4,"Finite pouring does not double count native mass");
  check(ShipFlooding.bail(level,wet,1)==1,"First bailing volume");load=(ShipFlooding.Load)field(control,"floodLoad");check(load.mass()==125,"Bailing removes exact water load");
  check(ShipFlooding.bail(level,wet,8)==1,"Final bailing volume");load=(ShipFlooding.Load)field(control,"floodLoad");check(load.mass()==0,"No ghost water mass after bailing");
  // Actual normal building still changes inertia by the new material's mass.
  level.setBlock(wet,Blocks.OAK_PLANKS.defaultBlockState(),2);
  check(Math.abs(ship.getInertiaData().getMass()-baseline-500)<1e-4,"Plank adds 500kg");
  level.setBlock(wet,Blocks.AIR.defaultBlockState(),2);
  check(Math.abs(ship.getInertiaData().getMass()-baseline)<1e-4,"Plank removal reverses its real mass");
  var dirty=control.getClass().getDeclaredField("blocksDirty");dirty.setAccessible(true);dirty.setBoolean(control,false);
  var notices=new BlockEvent.NeighborNotifyEvent(level,wet,Blocks.WATER.defaultBlockState(),EnumSet.allOf(Direction.class),false);
  for(int repeat=0;repeat<100;repeat++)RiggingEvents.neighbours(notices);
  check(!dirty.getBoolean(control),"Water neighbor storm does not restart the hull scan");
  RiggingEvents.neighbours(new BlockEvent.NeighborNotifyEvent(level,wet,Blocks.OAK_PLANKS.defaultBlockState(),EnumSet.allOf(Direction.class),false));
  check(dirty.getBoolean(control),"Structural neighbor change still invalidates rigging");
  // During a migration, massless fluid changes must not invalidate the accumulated dry hull.
  MassSync.invalidate();MassSync.tick(server);
  var jobs=MassSync.class.getDeclaredField("JOBS");jobs.setAccessible(true);Object job=((Map<?,?>)jobs.get(null)).get(id);
  check(job!=null,"Migration spans ticks on this hull");var changed=job.getClass().getDeclaredField("changed");changed.setAccessible(true);changed.setBoolean(job,false);
  var water=BlockStateInfo.INSTANCE.get(Blocks.WATER.defaultBlockState());
  var update=MassSync.beforeSetBlock(level,wet.getX(),wet.getY(),wet.getZ(),Blocks.AIR.defaultBlockState(),Blocks.WATER.defaultBlockState(),0,0,air.getSecond(),water.getSecond());
  check(!changed.getBoolean(job),"Massless fluid update leaves the mass snapshot valid");check(update.newType().equals(water.getSecond()),"Physics still receives its water voxel type");
  System.out.println("SHIP_WATER_NATIVE_PASS checks="+checks+" dryMassStable=true exactWaterLoad=true fluidVoxels=true realEdits=true noFluidScanRestarts=true");
 }
 void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||server==null)return;try{
  ticks++;if(ticks==2)build();
  if(ticks==15){ship=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);check(ship!=null,"Ship loaded");var b=ship.getShipAABB();for(int x=b.minX()>>4;x<=((b.maxX()-1)>>4);x++)for(int z=b.minZ()>>4;z<=((b.maxZ()-1)>>4);z++)level.setChunkForced(x,z,true);}
  if(ticks==160){tests();server.halt(false);}if(ticks>200)throw new AssertionError("Test deadline");
 }catch(Throwable t){System.out.println("SHIP_WATER_NATIVE_FAIL "+t);t.printStackTrace();server.halt(false);}}
}
