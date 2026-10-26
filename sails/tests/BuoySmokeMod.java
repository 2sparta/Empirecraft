package com.empirecraft.sails.test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mojang.authlib.GameProfile;
import com.quintonc.vs_sails.blocks.BuoyBlock;
import com.quintonc.vs_sails.registration.SailsBlocks;
import com.quintonc.vs_sails.ship.SailsShipControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.core.api.ships.*;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl;

/** Destructive fixture for a disposable water world. Never install on a real server. */
@Mod("empirecraft_buoy_smoketest")
public final class BuoySmokeMod {
    MinecraftServer server;ServerLevel level;ServerPlayer rider;int ticks,checks;
    final boolean resume=Boolean.getBoolean("empirecraft.buoy.resume");
    final boolean baseline=Boolean.getBoolean("empirecraft.buoy.baseline");
    final Map<String,Long> ids=new LinkedHashMap<>();
    final Map<String,Probe> probes=new LinkedHashMap<>();
    double maxRiderY=-Double.MAX_VALUE;
    public BuoySmokeMod(){
        ValkyrienSkies.api().registerAttachment(ValkyrienSkies.api().newAttachmentRegistrationBuilder(Probe.class).useLegacySerializer().build());
        MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    void started(ServerStartedEvent e){server=e.getServer();level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation("minecraft:world_europe")));if(level==null)throw new AssertionError("water fixture missing");}
    LoadedServerShip ship(String name){return VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(ids.get(name));}
    void assemble(String name,int x,int y,boolean hull){
        Set<BlockPos> blocks=new HashSet<>();
        for(int i=0;i<(name.equals("single")?1:4);i++)for(int z=0;z<(name.equals("single")?1:4);z++){
            BlockPos p=new BlockPos(x+i,y,z);level.setBlockAndUpdate(p,SailsBlocks.BUOY_BLOCK.get().defaultBlockState());blocks.add(p);
            if(hull){p=p.above();level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(p);}
        }
        if(hull){var p=new BlockPos(x+1,y+2,1);level.setBlockAndUpdate(p,SailsBlocks.HELM_BLOCK.get().defaultBlockState());blocks.add(p);}
        ServerShip body=ShipAssembler.assembleToShip(level,blocks,1);check(body!=null,name+" assembled");body.setStatic(true);ids.put(name,body.getId());
    }
    void release() throws Exception {
        for(String name:ids.keySet()){
            var body=ship(name);check(body!=null,name+" loaded");
            var c=SailsShipControl.getOrCreate(body,level);check(c.numBuoys==(name.equals("single")?1:16),name+" exact buoy recount");
            Probe p=new Probe();p.baseline=baseline;body.setAttachment(Probe.class,p);probes.put(name,p);body.setStatic(false);
        }
        if(!baseline){
            var box=ship("dry").getWorldAABB();
            rider.setPos((box.minX()+box.maxX())/2,box.maxY()+.01,(box.minZ()+box.maxZ())/2);rider.setOnGround(true);
        }
    }
    void removeHull(){
        var body=ship("remnant");var b=body.getShipAABB();List<BlockPos> remove=new ArrayList<>();
        for(int x=b.minX();x<b.maxX();x++)for(int y=b.minY();y<b.maxY();y++)for(int z=b.minZ();z<b.maxZ();z++){
            var p=new BlockPos(x,y,z);var block=level.getBlockState(p).getBlock();if(block!=Blocks.AIR && !(block instanceof BuoyBlock))remove.add(p);
        }
        for(var p:remove)level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
        check(remove.size()==17,"hull and helm removed, only buoys remain");
    }
    void finish() throws Exception {
        List<String> saved=new ArrayList<>();ids.forEach((name,id)->saved.add(name+"="+id));Files.write(Path.of("buoy-ships.txt"),saved);
        for(var e:probes.entrySet()){
            var p=e.getValue();System.out.println("BUOY_TRACE "+e.getKey()+" mass="+p.mass+" maxY="+p.maxY+" lateRange="+(p.lateMax-p.lateMin)+" maxUp="+p.maxUp+" lastY="+p.lastY+" overlap="+p.overlap+" factor="+p.factor+" steps="+p.steps);
        }
        for(var e:probes.entrySet()){
            var p=e.getValue();
            check(p.steps>60,"real physics sampled");
            if(!baseline){check(p.failure==null,"finite bounded physics: "+p.failure);
                check(p.mass>=(e.getKey().equals("single")?99:1599),"new or migrated buoy mass applied in real physics");
                check(p.maxY<(e.getKey().equals("dry")?124:28),"no airborne launch: "+e.getKey());
                check(p.maxUp<4.0,"no vertical impulse spike: "+e.getKey()+" "+p.maxUp);
                if(!e.getKey().equals("dry")){check(e.getKey().equals("loaded")?(p.lastY>20 && p.lastY<23):(p.lastY>20.2 && p.lastY<21),"buoys remain at the surface: "+e.getKey()+" "+p.lastY);check(p.lateMax-p.lateMin<.01,"settled buoys do not bounce: "+e.getKey());}
                else check(p.lastY<119,"airborne old/new buoy raft descends without lift");
            }
        }
        if(!baseline){check(maxRiderY<125,"standing player stays within normal world height");check(ship("remnant").getAttachment(SailsShipControl.class).numBuoys==16,"buoy-only remnant remains attached");}
        System.out.println("BUOY_SMOKE_"+(baseline?"BASELINE":"PASS")+" assertions="+checks+" riderMaxY="+maxRiderY);server.halt(false);server=null;
    }
    void tick(TickEvent.ServerTickEvent e){if(server==null||e.phase!=TickEvent.Phase.END)return;try{
        ticks++;
        if(ticks==10){
            for(int x=-2;x<=10;x++)for(int z=-2;z<=2;z++)level.setChunkForced(x,z,true);
            rider=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("46c5f72f-9bcc-4ff4-a155-7e48288d33c4"),"BuoyRider"));
            rider.setPos(8,35,8);level.addNewPlayer(rider);
            for(var f:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(f.getType()==List.class){f.setAccessible(true);try{((List)f.get(server.getPlayerList())).add(rider);break;}catch(UnsupportedOperationException ignored){}}
            if(resume){for(String line:Files.readAllLines(Path.of("buoy-ships.txt"))){String[] parts=line.split("=");ids.put(parts[0],Long.parseLong(parts[1]));}}
            else{assemble("dry",0,120,false);assemble("wet",32,21,false);assemble("remnant",64,21,true);assemble("loaded",96,21,true);assemble("single",128,21,false);}
        }
        if(ticks==35 && resume){for(String name:ids.keySet()){
            var body=ship(name);check(body!=null,"old saved ship loaded");body.setStatic(true);
            double x=name.equals("dry")?2:name.equals("wet")?34:name.equals("remnant")?66:98,y=name.equals("dry")?120.5:21.5;
            if(name.equals("dry"))VSGameUtilsKt.getShipObjectWorld(level).teleportShip(body,new ShipTeleportDataImpl(new Vector3d(x,y,2),new Quaterniond(),new Vector3d(),new Vector3d(),body.getChunkClaimDimension(),1.0,body.getTransform().getPositionInShip()));
        }}
        if(ticks==35){for(String name:ids.keySet()){
            var b=ship(name).getShipAABB();for(int x=b.minX()>>4;x<=((b.maxX()-1)>>4);x++)for(int z=b.minZ()>>4;z<=((b.maxZ()-1)>>4);z++)level.setChunkForced(x,z,true);
        }}
        if(ticks==65)release();
        if(ticks==100 && !resume)removeHull();
        if(rider!=null)maxRiderY=Math.max(maxRiderY,rider.getY());
        if(ticks==600)finish();
    }catch(Throwable ex){System.out.println("BUOY_SMOKE_FAIL "+ex);ex.printStackTrace();server.halt(false);server=null;}}
    public static final class Probe implements ShipPhysicsListener {
        @JsonIgnore public boolean baseline;
        @JsonIgnore public volatile double maxY=-Double.MAX_VALUE,maxUp=-Double.MAX_VALUE,lastY,mass,overlap,factor;
        @JsonIgnore public volatile double lateMin=Double.POSITIVE_INFINITY,lateMax=-Double.POSITIVE_INFINITY;
        @JsonIgnore public volatile int steps;
        @JsonIgnore public volatile String failure;
        public void physTick(PhysShip body,PhysLevel world){
            double y=body.getTransform().getPositionInWorld().y(),v=body.getVelocity().y();steps++;mass=body.getMass();overlap=body.getLiquidOverlap();factor=body.getBuoyantFactor();lastY=y;
            maxY=Math.max(maxY,y);maxUp=Math.max(maxUp,v);
            if(steps>1200){lateMin=Math.min(lateMin,y);lateMax=Math.max(lateMax,y);}
            if(!Double.isFinite(y)||!Double.isFinite(v)||!Double.isFinite(factor))failure="non-finite pose/force";
            // Keep a reproducing baseline from leaving the disposable test world and crashing its native physics.
            if(baseline && (y>240 || v>100))body.setStatic(true);
        }
    }
}
