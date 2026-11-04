package com.empirecraft.sails.test;

import com.quintonc.vs_sails.registration.SailsBlocks;
import com.quintonc.vs_sails.ship.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.world.connectivity.ConnectionStatus;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/** Destructive sea/cabin fixture. Only sea_smoke.py's fresh disposable world is suitable. */
@Mod("empirecraft_sea_smoketest")
public final class SeaSmokeMod {
    MinecraftServer server;ServerLevel level;int ticks,checks,waterSounds,creaks;
    long raftId,cabinId;BlockPos interior,hole;ArmorStand passenger;
    double clearMotion,rainMotion,clearImmersion;int clearSamples,rainSamples;
    SeaProbe probe;
    public SeaSmokeMod(){
        var api=ValkyrienSkies.api();api.registerAttachment(api.newAttachmentRegistrationBuilder(SeaProbe.class).useLegacySerializer().build());
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::sound);
    }
    void started(ServerStartedEvent e){server=e.getServer();level=server.overworld();com.quintonc.vs_sails.util.SailsCommands.debug=true;}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    LoadedServerShip ship(long id){return VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);}
    ConnectionStatus cabin(){return VSGameUtilsKt.getShipObjectWorld(level).isIsolatedAir(interior.getX(),interior.getY(),interior.getZ(),VSGameUtilsKt.getDimensionId(level));}
    void build(){
        check(ValkyrienSkies.isConnectivityEnabled(false),"installed world config enables native connectivity");
        check(!org.valkyrienskies.core.impl.config.VSCoreConfig.SERVER.getSp().getEnableSplitting(),"air pockets do not enable ship splitting");
        System.out.println("SEA_PHYSICS_CONFIG speed="+org.valkyrienskies.core.impl.config.VSCoreConfig.SERVER.getPhysics().getPhysicsSpeed());
        // Keep the same real-player presence as the existing buoy integration fixture.
        var rider=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(
                java.util.UUID.fromString("eac32ef9-732a-470c-8a7d-cd982dc947b8"),"SeaRider"));
        rider.setPos(8,80,8);level.addNewPlayer(rider);
        for(var f:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(f.getType()==java.util.List.class){
            try{f.setAccessible(true);((java.util.List)f.get(server.getPlayerList())).add(rider);break;}catch(ReflectiveOperationException | UnsupportedOperationException ignored){}
        }
        for(int x=-1;x<6;x++)for(int z=-1;z<4;z++)level.setChunkForced(x,z,true);
        Set<BlockPos> blocks=new HashSet<>();
        for(int x=0;x<4;x++)for(int z=0;z<4;z++){
            BlockPos pos=new BlockPos(x,71,z);level.setBlockAndUpdate(pos,SailsBlocks.BUOY_BLOCK.get().defaultBlockState());blocks.add(pos);
        }
        var raft=ShipAssembler.assembleToShip(level,blocks,1);raft.setStatic(true);raftId=raft.getId();
        blocks=new HashSet<>();
        for(int x=32;x<39;x++)for(int y=63;y<69;y++)for(int z=16;z<23;z++){
            BlockPos pos=new BlockPos(x,y,z);
            if(x==32 || x==38 || y==63 || y==68 || z==16 || z==22){
                level.setBlockAndUpdate(pos,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(pos);
            }else level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        }
        var cabin=ShipAssembler.assembleToShip(level,blocks,1);cabin.setStatic(true);cabinId=cabin.getId();
    }
    void loaded(){
        for(long id:new long[]{raftId,cabinId}){
            var body=ship(id);check(body!=null,"assembled body is loaded");var b=body.getShipAABB();
            for(int x=b.minX()>>4;x<=(b.maxX()-1)>>4;x++)for(int z=b.minZ()>>4;z<=(b.maxZ()-1)>>4;z++)level.setChunkForced(x,z,true);
            SailsShipControl.getOrCreate(body,level).onServerTick();
        }
        var b=ship(cabinId).getShipAABB();
        interior=new BlockPos(b.minX()+3,b.minY()+1,b.minZ()+3);
        hole=new BlockPos(b.minX(),b.minY()+2,b.minZ()+3);
        var p=ship(cabinId).getShipToWorld().transformPosition(new Vector3d(interior.getX()+.5,interior.getY(),interior.getZ()+.5));
        passenger=new ArmorStand(level,p.x,p.y,p.z);passenger.setNoGravity(true);level.addFreshEntity(passenger);
        probe=new SeaProbe();ship(raftId).setAttachment(SeaProbe.class,probe);
    }
    void refreshPassenger(){
        passenger.tickCount=0;SealedCabins.refresh(passenger);
    }
    void sound(net.minecraftforge.event.PlayLevelSoundEvent.AtPosition event){
        var id=event.getSound().value().getLocation();
        if(id.equals(ShipSounds.WATER.getLocation()) || id.equals(ShipSounds.CREAK.getLocation())){
            check(Math.abs(event.getPosition().x)<1000 && Math.abs(event.getPosition().z)<1000,"sound uses world position, not ship storage");
            if(id.equals(ShipSounds.WATER.getLocation()))waterSounds++;else creaks++;
        }
    }
    void sample(boolean rain)throws Exception{
        var body=ship(raftId);var omega=new Vector3d(probe.omegaX,0,probe.omegaZ);
        check(omega.isFinite() && omega.length()<1,"bounded native wave motion");
        check(!body.isStatic(),"free raft is not frozen by wave control");
        double energy=omega.x()*omega.x()+omega.z()*omega.z();
        if(rain){rainMotion+=energy;rainSamples++;}
        else{
            clearMotion+=energy;clearSamples++;
            Field field=SailsShipControl.class.getDeclaredField("hullImmersion");field.setAccessible(true);
            clearImmersion+=field.getDouble(body.getAttachment(SailsShipControl.class));
        }
    }
    void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END || server==null)return;
        try{
            ticks++;
            if(ticks==5)build();
            if(ticks==20)loaded();
            if(ticks==65)ship(raftId).setStatic(false);
            if(ticks==120){
                check(VSGameUtilsKt.isPositionSealed(level,interior),"sealed lower deck has a confirmed air pocket");
                refreshPassenger();check(((IEntityDraggingInformationProvider)passenger).vs$isInSealedArea(),"passenger is inside a dry cabin");
                check(!passenger.isInWater(),"ocean water does not swim-push passenger through hull");
                check(level.getBlockState(interior).isAir(),"dry cabin keeps its real air blocks");
                level.setBlockAndUpdate(hole,Blocks.AIR.defaultBlockState());
                check(level.getBlockState(hole).isAir(),"breach removes an actual hull block");
                System.out.println("SEA_CABIN_BREACH bounds="+ship(cabinId).getShipAABB()+" interior="+interior+" hole="+hole+" outside="+level.getBlockState(hole.east()));
            }
            if(ticks==160){
                check(!VSGameUtilsKt.isPositionSealed(level,interior),"hull breach connects cabin to exterior even if native status remains "+cabin());
                // Reproduce the old stale native cache while the passenger stays in the same block.
                ((IEntityDraggingInformationProvider)passenger).vs$setInSealedArea(true);
                refreshPassenger();check(!((IEntityDraggingInformationProvider)passenger).vs$isInSealedArea(),"stationary passenger loses stale dry protection after breach");
                level.setBlockAndUpdate(hole,Blocks.OAK_PLANKS.defaultBlockState());
            }
            if(ticks==200){
                check(VSGameUtilsKt.isPositionSealed(level,interior),"repair restores cabin sealing");
                refreshPassenger();check(((IEntityDraggingInformationProvider)passenger).vs$isInSealedArea(),"repair restores dry passenger state");
            }
            if(ticks>=220 && ticks<460)sample(false);
            if(ticks==220 || ticks==460 || ticks==820){
                var raft=ship(raftId);var c=raft.getAttachment(SailsShipControl.class);
                System.out.println("SEA_NATIVE_TRACE tick="+ticks+" pos="+raft.getTransform().getPositionInWorld()+" rotation="+raft.getTransform().getShipToWorldRotation()+" omega="+raft.getAngularVelocity()+" physicsSteps="+probe.steps+" physicalY="+probe.y+" sleeping="+probe.sleeping+" physicalStatic="+probe.staticBody+" buoys="+c.numBuoys+" debug="+(c.message==null?"null":c.message.getString()));
            }
            if(ticks==460){level.setWeatherParameters(0,10000,true,true);level.setRainLevel(1);level.setThunderLevel(1);}
            if(ticks>=520 && ticks<820)sample(true);
            if(ticks==820){
                double clear=Math.sqrt(clearMotion/clearSamples),rain=Math.sqrt(rainMotion/rainSamples),draft=clearImmersion/clearSamples;
                System.out.println("SEA_MOTION_NATIVE_TRACE clear="+clear+" rain="+rain+" draft="+draft+" water="+waterSounds+" creaks="+creaks);
                check(clear>1e-5,"clear weather produces real gentle rocking");
                check(rain>clear*2,"rain produces stronger real rocking: "+rain+" / "+clear);
                check(draft>.2 && draft<.55,"real buoy raft has visible draft: "+draft);
                check(waterSounds>0 && creaks>0,"registered positional water and hull foley played");
                check(waterSounds<40 && creaks<20,"foley stays rate limited per ship");
                System.out.println("SEA_SMOKE_PASS assertions="+checks+" clearRms="+clear+" rainRms="+rain+" immersion="+draft+" waterSounds="+waterSounds+" creaks="+creaks);
                server.halt(false);server=null;
            }
        }catch(Throwable failure){System.out.println("SEA_SMOKE_FAIL tick="+ticks);failure.printStackTrace();server.halt(false);server=null;}
    }
    public static final class SeaProbe implements org.valkyrienskies.core.api.ships.ShipPhysicsListener {
        @com.fasterxml.jackson.annotation.JsonIgnore public volatile double omegaX,omegaZ,y;
        @com.fasterxml.jackson.annotation.JsonIgnore public volatile boolean sleeping,staticBody;
        @com.fasterxml.jackson.annotation.JsonIgnore public volatile int steps;
        public void physTick(org.valkyrienskies.core.api.ships.PhysShip body,org.valkyrienskies.core.api.world.PhysLevel world){
            omegaX=body.getAngularVelocity().x();omegaZ=body.getAngularVelocity().z();y=body.getTransform().getPositionInWorld().y();
            sleeping=((org.valkyrienskies.core.impl.game.ships.PhysShipImpl)body).isSleeping();staticBody=body.isStatic();steps++;
        }
    }
}
