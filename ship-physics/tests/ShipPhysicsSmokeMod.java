package ua.empirecraft.shipphysics.test;

import com.quintonc.vs_sails.registration.SailsBlocks;
import com.quintonc.vs_sails.ship.SailsShipControl;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import ua.empirecraft.shipphysics.*;
import java.lang.reflect.*;
import java.util.*;

/** Destructive test fixture; launch only in smoke.py's disposable world. */
@Mod("empirecraft_physics_smoketest")
public final class ShipPhysicsSmokeMod {
    MinecraftServer server;ServerLevel level;int ticks,checks,editTick=-1;
    long large,radial;double beforeY,minY,baseMass;
    boolean sawPending;int edits;
    public ShipPhysicsSmokeMod(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void started(ServerStartedEvent e){server=e.getServer();level=server.overworld();PhysicsConfig.get().collisionProbe=false;}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    LoadedServerShip ship(long id){return VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);}
    void build(){
        for(int x=-1;x<8;x++)for(int z=-1;z<6;z++)level.setChunkForced(x,z,true);
        Set<BlockPos> blocks=new HashSet<>();
        for(int x=0;x<32;x++)for(int z=0;z<64;z++){
            BlockPos p=new BlockPos(x,71,z);level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(p);
        }
        // A tall sparse mast forces several bounded scans, as on a large vessel.
        for(int y=72;y<=103;y++){
            BlockPos p=new BlockPos(16,y,32);level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(p);
        }
        BlockPos sail=new BlockPos(17,103,32);level.setBlockAndUpdate(sail,SailsBlocks.SAIL_BLOCK.get().defaultBlockState());blocks.add(sail);
        var body=ShipAssembler.assembleToShip(level,blocks,1);large=body.getId();
        blocks=new HashSet<>();
        for(int x=80;x<=88;x++)for(int z=0;z<=8;z++)for(int y:new int[]{100,103}){
            BlockPos p=new BlockPos(x,y,z);level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(p);
        }
        body=ShipAssembler.assembleToShip(level,blocks,1);body.setStatic(true);radial=body.getId();
    }
    void loaded(){
        for(long id:new long[]{large,radial}){
            var body=ship(id);check(body!=null,"assembled ship loaded");var b=body.getShipAABB();
            if(id==large){
                int end=(b.maxX()+15)&~15;
                for(int x=b.maxX();x<end;x++)level.setBlockAndUpdate(new BlockPos(x,b.minY(),b.minZ()+4),Blocks.OAK_PLANKS.defaultBlockState());
                b=body.getShipAABB();
                check((b.maxX()&15)==0,"native mass scan ends exactly at a chunk boundary");
            }
            for(int x=b.minX()>>4;x<=(b.maxX()-1)>>4;x++)for(int z=b.minZ()>>4;z<=(b.maxZ()-1)>>4;z++)level.setChunkForced(x,z,true);
            SailsShipControl.getOrCreate(body,level).onServerTick();
        }
    }
    void edit()throws Exception{
        var body=ship(large);var c=SailsShipControl.getOrCreate(body,level);var b=body.getShipAABB();
        check((Boolean)field(c,"hullReady"),"complete initial hull snapshot");
        check(!MassSync.busy(large),"initial atomic mass migration completed");
        check(!body.isStatic(),"ordinary ship released after initial mass scan");
        baseMass=body.getInertiaData().getMass();beforeY=body.getTransform().getPositionInWorld().y();minY=beforeY;
        BlockPos p=edits==0 ? new BlockPos(b.minX()+4,b.minY()+1,b.minZ()+4)
                : new BlockPos(b.maxX(),b.minY(),b.minZ()+4); // Second edit expands bounds.
        level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());
        SailsShipControl.markDirty(level,p);
        check((Boolean)field(c,"hullReady"),"edit retains completed hull readiness");
        check(field(c,"countedHull")!=null,"edit retains displacement snapshot");
        c.onServerTick();
        check(field(c,"blockScan")!=null,"large hull scan spans multiple ticks");
        check(field(c,"countedHull")!=null,"snapshot survives a bounds-changing recount");
        check(Math.abs(body.getInertiaData().getMass()-baseMass-500)<1e-3,"one placed plank adds only its mass");
        editTick=ticks;sawPending=true;
    }
    void checkEdit()throws Exception{
        var body=ship(large);var c=SailsShipControl.getOrCreate(body,level);
        minY=Math.min(minY,body.getTransform().getPositionInWorld().y());
        check((Boolean)field(c,"hullReady"),"lift remains enabled throughout recount");
        check(!c.motionSafetyTripped && !c.sailSafetyHeld,"normal edit never triggers emergency/cloth hold");
        check(!MassSync.busy(large),"normal edit does not restart whole-ship mass migration");
        if(ticks-editTick>=24){
            check(field(c,"blockScan")==null,"bounded recount finishes");
            check(beforeY-minY<0.3,"no transient sinking after one block: drop="+(beforeY-minY));
            check(Math.abs(body.getInertiaData().getMass()-baseMass-500)<1e-3,"recount preserves incremental mass");
            System.out.println("PHYSICS_EDIT_PASS edit="+edits+" maxDrop="+(beforeY-minY));
            edits++;editTick=-1;
        }
    }
    void collision()throws Exception{
        // This isolated fixture has no Towny. Its separate cancellation assertion
        // exercises Forge denial; sails/safety_smoke.py tests real Towny access.
        com.quintonc.vs_sails.protection.ProtectionConfig.get().enabled=false;
        var body=ship(radial);var b=body.getShipAABB();
        BlockPos centre=new BlockPos(b.minX()+4,b.minY(),b.minZ()+4);
        Class<?> target=Class.forName("ua.empirecraft.shipphysics.CollisionDamage$Target");
        Constructor<?> constructor=target.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var state=level.getBlockState(centre);
        Object t=constructor.newInstance(centre,BlockProperties.at(level,centre,state),
                new Vector3d(centre.getX()+.5,centre.getY(),centre.getZ()+.5),new Vector3d(0,1,0),1.0,BlockProperties.responseAt(level,centre,state));
        Method plan=CollisionDamage.class.getDeclaredMethod("plan",ServerLevel.class,LoadedServerShip.class,target,double.class);plan.setAccessible(true);
        Object work=plan.invoke(null,level,body,t,200000.0);
        List<?> cells=(List<?>)field(work,"cells");
        check(cells.size()==49,"impact sphere reaches 49 adjacent floor cells");
        for(Object cell:cells)check(((BlockPos)field(cell,"pos")).getY()==b.minY(),"impact cannot cross air to upper deck");
        double fractions=0;for(Object cell:cells)fractions+=(Double)field(cell,"share");
        check(Math.abs(fractions-1)<1e-12,"runtime damage shares conserve energy");
        Field pending=CollisionDamage.class.getDeclaredField("PENDING");pending.setAccessible(true);
        ((Deque<Object>)pending.get(null)).add(work);
        Method drain=CollisionDamage.class.getDeclaredMethod("drain",MinecraftServer.class);drain.setAccessible(true);drain.invoke(null,server);
        check(level.getBlockState(centre).isAir(),"contact block breaks through real server destruction");
        for(BlockPos p:List.of(centre.east(),centre.west(),centre.north(),centre.south()))check(level.getBlockState(p).isAir(),"damage spreads sideways");
        for(int x=b.minX();x<b.maxX();x++)for(int z=b.minZ();z<b.maxZ();z++)check(!level.getBlockState(new BlockPos(x,b.minY()+3,z)).isAir(),"upper deck intact");
        check(((Deque<?>)pending.get(null)).isEmpty(),"damage work fully drained");
        // Forge cancellation must still stop collateral damage at a protected origin.
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent> deny=e->{if(e.getPos().getX()==b.minX())e.setCanceled(true);};
        MinecraftForge.EVENT_BUS.addListener(deny);
        BlockPos protectedPos=new BlockPos(b.minX(),b.minY(),b.minZ());state=level.getBlockState(protectedPos);
        t=constructor.newInstance(protectedPos,BlockProperties.at(level,protectedPos,state),new Vector3d(protectedPos.getX()+.5,protectedPos.getY(),protectedPos.getZ()+.5),new Vector3d(0,1,0),1.0,BlockProperties.responseAt(level,protectedPos,state));
        work=plan.invoke(null,level,body,t,200000.0);((Deque<Object>)pending.get(null)).add(work);drain.invoke(null,server);
        check(!level.getBlockState(protectedPos).isAir(),"Forge protection cancellation honoured");
        MinecraftForge.EVENT_BUS.unregister(deny);
        com.quintonc.vs_sails.protection.ProtectionConfig.get().enabled=true;
    }
    void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END || server==null)return;
        try{
            ticks++;
            if(ticks==5)build();
            if(ticks==15)loaded();
            if(ticks>=180 && edits<2){if(editTick<0)edit();else checkEdit();}
            if(edits==2){collision();System.out.println("SHIP_PHYSICS_SMOKE_PASS assertions="+checks+" pendingScan="+sawPending);server.halt(false);server=null;}
            if(ticks>300)throw new AssertionError("fixture timeout");
        }catch(Throwable failure){System.out.println("SHIP_PHYSICS_SMOKE_FAIL ticks="+ticks);failure.printStackTrace();server.halt(false);server=null;}
    }
}
