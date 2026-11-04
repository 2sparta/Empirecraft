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
    Probe probe;
    public ShipPhysicsSmokeMod(){
        var api=org.valkyrienskies.mod.api.ValkyrienSkies.api();
        api.registerAttachment(api.newAttachmentRegistrationBuilder(Probe.class).useLegacySerializer().build());
        MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
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
        int[] sounds={0};
        java.util.function.Consumer<net.minecraftforge.event.PlayLevelSoundEvent.AtPosition> hear=e->{
            sounds[0]++;
            check(Math.abs(e.getPosition().x)<1000 && Math.abs(e.getPosition().z)<1000,"impact sound is located in world coordinates");
        };
        MinecraftForge.EVENT_BUS.addListener(hear);
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
        check(sounds[0]==1,"radial destruction emits one rate-limited ship impact sound");
        // Forge cancellation must still stop collateral damage at a protected origin.
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent> deny=e->{if(e.getPos().getX()==b.minX())e.setCanceled(true);};
        MinecraftForge.EVENT_BUS.addListener(deny);
        ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+5); // A denied hit must stay silent even after the sound cooldown.
        BlockPos protectedPos=new BlockPos(b.minX(),b.minY(),b.minZ());state=level.getBlockState(protectedPos);
        t=constructor.newInstance(protectedPos,BlockProperties.at(level,protectedPos,state),new Vector3d(protectedPos.getX()+.5,protectedPos.getY(),protectedPos.getZ()+.5),new Vector3d(0,1,0),1.0,BlockProperties.responseAt(level,protectedPos,state));
        work=plan.invoke(null,level,body,t,200000.0);((Deque<Object>)pending.get(null)).add(work);drain.invoke(null,server);
        check(!level.getBlockState(protectedPos).isAir(),"Forge protection cancellation honoured");
        check(sounds[0]==1,"denied damage emits no impact sound");
        MinecraftForge.EVENT_BUS.unregister(deny);
        ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+5);
        work=plan.invoke(null,level,body,t,1.0);((Deque<Object>)pending.get(null)).add(work);drain.invoke(null,server);
        check(!level.getBlockState(protectedPos).isAir() && sounds[0]==2,"partial damage plays a material hit without breaking the block");
        MinecraftForge.EVENT_BUS.unregister(hear);
        com.quintonc.vs_sails.protection.ProtectionConfig.get().enabled=true;
    }
    void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END || server==null)return;
        try{
            ticks++;
            if(ticks==5)build();
            if(ticks==15)loaded();
            if(ticks>=180 && edits<2){if(editTick<0)edit();else checkEdit();}
            if(edits==2 && probe==null){probe=new Probe();probe.control=SailsShipControl.getOrCreate(ship(large),level);ship(large).setAttachment(Probe.class,probe);}
            if(probe!=null && probe.complete){
                check(probe.failure==null,"native headwind/trim probe: "+probe.failure);
                checks+=probe.checks;ship(large).removeAttachment(Probe.class);
                collision();System.out.println("SHIP_PHYSICS_SMOKE_PASS assertions="+checks+" pendingScan="+sawPending);server.halt(false);server=null;
            }
            if(ticks>300)throw new AssertionError("fixture timeout");
        }catch(Throwable failure){System.out.println("SHIP_PHYSICS_SMOKE_FAIL ticks="+ticks);failure.printStackTrace();server.halt(false);server=null;}
    }
    public static final class Probe implements org.valkyrienskies.core.api.ships.ShipPhysicsListener {
        @com.fasterxml.jackson.annotation.JsonIgnore public SailsShipControl control;
        @com.fasterxml.jackson.annotation.JsonIgnore public volatile boolean complete;
        @com.fasterxml.jackson.annotation.JsonIgnore public String failure;
        @com.fasterxml.jackson.annotation.JsonIgnore public int checks;
        void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
        static void set(Object object,String name,Object value)throws Exception{
            Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);
        }
        @SuppressWarnings("unchecked")
        Vector3d[] forces(org.valkyrienskies.core.impl.game.ships.PhysShipImpl body,org.valkyrienskies.core.api.world.PhysLevel level)throws Exception{
            var forces=(ArrayDeque<org.joml.Vector3dc>)field(body,"invPosForces");
            var positions=(ArrayDeque<org.joml.Vector3dc>)field(body,"invPosPositions");
            var torques=(ArrayDeque<org.joml.Vector3dc>)field(body,"invTorques");
            int nf=forces.size(),np=positions.size(),nt=torques.size();
            ((com.quintonc.vs_sails.ship.SailDrive)field(control,"sailDrive")).reset();
            control.physTick(body,level);Vector3d force=new Vector3d(),torque=new Vector3d();
            while(forces.size()>nf)force.add(forces.removeLast());
            while(positions.size()>np)positions.removeLast();
            while(torques.size()>nt)torque.add(torques.removeLast());
            return new Vector3d[]{force,torque};
        }
        public void physTick(org.valkyrienskies.core.api.ships.PhysShip physical,org.valkyrienskies.core.api.world.PhysLevel level){
            if(complete || control==null)return;
            var body=(org.valkyrienskies.core.impl.game.ships.PhysShipImpl)physical;var saved=body.getPoseVel();Object oldWind=null;
            try{
                oldWind=field(control,"sampledWind");
                var ctor=oldWind.getClass().getDeclaredConstructors()[0];ctor.setAccessible(true);
                check(field(control,"stabilityFrame")!=null,"ordinary wooden hull samples actual water");
                var rotation=new org.joml.Quaterniond();
                Vector3d heading=com.quintonc.vs_sails.ship.ArcadeSailing.heading(rotation,control.shipDirection.getOpposite().getStepX(),control.shipDirection.getOpposite().getStepZ());
                double along=Math.toDegrees(Math.atan2(heading.z,heading.x));
                set(control,"sampledWind",ctor.newInstance(along+180,1.0));
                body.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,new Vector3d(),new Vector3d()));
                var stopped=forces(body,level);check(Math.abs(stopped[0].dot(heading))<1e-6,"real hull cannot launch into headwind");
                body.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,new Vector3d(heading).mul(18),new Vector3d()));
                var moving=forces(body,level);check(moving[0].dot(heading)/body.getMass()<-2.9,"real heavy hull brakes against wind");
                for(double pitch:new double[]{-.25,.25}){
                    rotation=new org.joml.Quaterniond().rotateX(pitch);
                    body.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(saved.b,rotation,new Vector3d(),new Vector3d()));
                    var tilted=forces(body,level);
                    check(tilted[1].x*pitch<0,"real water-only restoring torque at zero speed: pitch="+pitch+" torque="+tilted[1]+" position="+saved.b);
                    body.setPoseVel(new org.valkyrienskies.core.impl.shadow.Hg(new Vector3d(saved.b).add(0,50,0),rotation,new Vector3d(),new Vector3d()));
                    var dry=forces(body,level);check(dry[1].lengthSquared()<1e-9,"same hull receives no trim torque in air");
                }
            }catch(Throwable error){failure=error.toString();error.printStackTrace();}
            finally{
                try{if(oldWind!=null)set(control,"sampledWind",oldWind);}catch(Exception ignored){}
                body.setPoseVel(saved);complete=true;
            }
        }
    }
}
