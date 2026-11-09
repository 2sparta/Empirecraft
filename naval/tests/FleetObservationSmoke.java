package com.empirecraft.fleetobservationtest;

import java.lang.reflect.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import ua.empirecraft.townymap.FleetObservationNative;

/** Disposable native VS/Mohist test; this class is never included in production JARs. */
@Mod("empirecraft_observation_test")
public final class FleetObservationSmoke {
    MinecraftServer server;ServerLevel level;ServerPlayer player;ServerShip ship;int ticks,checks;
    Map<String,Object> remembered;Vec3 point;Vector3d initial;
    public FleetObservationSmoke(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void started(ServerStartedEvent event){server=event.getServer();level=server.overworld();}
    void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);System.out.println("OBSERVATION_CHECK "+message);}
    static io.netty.channel.embedded.EmbeddedChannel sink(){return new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelOutboundHandlerAdapter(){
        @Override public void write(io.netty.channel.ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise){io.netty.util.ReferenceCountUtil.release(message);promise.trySuccess();}
        @Override public void flush(io.netty.channel.ChannelHandlerContext context){}
    });}
    void setup()throws Exception {
        for(int x=-3;x<=5;x++)for(int z=-3;z<=5;z++)level.setChunkForced(x,z,true);
        player=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes("FleetObservation".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"FleetObserver"));
        player.connection.connection.channel=sink();player.connection.connection.channel.attr(net.minecraft.network.Connection.ATTRIBUTE_PROTOCOL).set(net.minecraft.network.ConnectionProtocol.PLAY);player.setNoGravity(true);player.setPos(5,82,-12);level.addNewPlayer(player);
        for(Field f:net.minecraft.server.players.PlayerList.class.getDeclaredFields()){
            f.setAccessible(true);if(f.getType()==List.class)try{((List)f.get(server.getPlayerList())).add(player);break;}catch(UnsupportedOperationException ignored){}
        }
        for(Field f:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(Map.class.isAssignableFrom(f.getType())&&f.getGenericType().getTypeName().contains("java.util.UUID")&&f.getGenericType().getTypeName().contains("ServerPlayer")){f.setAccessible(true);((Map)f.get(server.getPlayerList())).put(player.getUUID(),player);}
        Set<BlockPos> blocks=new HashSet<>();for(int x=5;x<=9;x++)for(int z=0;z<=3;z++){var p=new BlockPos(x,80,z);level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(p);}
        ship=ShipAssembler.assembleToShip(level,blocks,1);ship.setStatic(true);var bounds=ship.getShipAABB();for(int x=bounds.minX()>>4;x<=(bounds.maxX()-1)>>4;x++)for(int z=bounds.minZ()>>4;z<=(bounds.maxZ()-1)>>4;z++)level.setChunkForced(x,z,true);
    }
    void face(Vec3 target){Vec3 d=target.subtract(player.getEyePosition());player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));player.setYHeadRot(player.getYRot());player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
    void moveShip(Vector3d position){VSGameUtilsKt.getShipObjectWorld(level).teleportShip(ship,new ShipTeleportDataImpl(position,new Quaterniond(ship.getTransform().getShipToWorldRotation()),new Vector3d(),new Vector3d(),ship.getChunkClaimDimension(),1.0,ship.getTransform().getPositionInShip()));}
    void tick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END||server==null)return;try{
        ticks++;if(ticks==5)setup();
        if(ticks==35){
            var bounds=ship.getWorldAABB();check(bounds!=null,"real assembled ship has world bounds");point=new Vec3((bounds.minX()+bounds.maxX())*.5,(bounds.minY()+bounds.maxY())*.5,(bounds.minZ()+bounds.maxZ())*.5);initial=new Vector3d(ship.getTransform().getPositionInWorld());
            Object bukkit=level.getClass().getMethod("getWorld").invoke(level);String world=bukkit.getClass().getMethod("getUID").invoke(bukkit).toString();
            remembered=new HashMap<>(Map.of("player",player.getUUID().toString(),"ship",ship.getId(),"world",world,"x",point.x,"y",point.y,"z",point.z));player.setPos(point.x,point.y+2,point.z-12);face(point);
            check(!FleetObservationNative.empty(remembered),"a present hull keeps its marker");moveShip(new Vector3d(initial).add(1000,0,0));
        }
        if(ticks==65){
            face(point);check(FleetObservationNative.empty(remembered),"visible empty old position is verified after the actual ship moves away");
            var wrong=new HashMap<>(remembered);wrong.put("world",UUID.randomUUID().toString());check(!FleetObservationNative.empty(wrong),"another dimension cannot clear the observation");wrong=new HashMap<>(remembered);wrong.put("x",Double.NaN);check(!FleetObservationNative.empty(wrong),"invalid saved coordinates cannot clear a marker");wrong=new HashMap<>(remembered);wrong.put("player",UUID.randomUUID().toString());check(!FleetObservationNative.empty(wrong),"offline observers cannot clear markers");
            player.setPos(point.x+80,point.y+2,point.z-12);face(point);check(!FleetObservationNative.empty(remembered),"a remote old position is retained");
            player.setPos(point.x,point.y+2,point.z-32);face(point);player.setYRot(player.getYRot()+180);player.setYHeadRot(player.getYRot());check(!FleetObservationNative.empty(remembered),"looking away from a distant old position retains it");
            player.setPos(point.x,point.y+2,point.z-12);face(point);int wall=(int)Math.floor(point.z-5);
            for(int x=(int)point.x-5;x<=(int)point.x+5;x++)for(int y=(int)point.y-5;y<=(int)point.y+8;y++)level.setBlockAndUpdate(new BlockPos(x,y,wall),Blocks.STONE.defaultBlockState());
            check(!FleetObservationNative.empty(remembered),"terrain occlusion does not reveal an absent ship through a wall");
            for(int x=(int)point.x-5;x<=(int)point.x+5;x++)for(int y=(int)point.y-5;y<=(int)point.y+8;y++)level.setBlockAndUpdate(new BlockPos(x,y,wall),Blocks.AIR.defaultBlockState());
            player.setPos(point.x+2,point.y+2,point.z+2);player.setYRot(0);player.setXRot(-80);check(FleetObservationNative.empty(remembered),"physical arrival confirms a nearby empty berth without aiming at a pixel");
            var unloaded=new HashMap<>(remembered);unloaded.put("x",1000000.0);player.setPos(1000000,point.y+2,point.z-12);check(!FleetObservationNative.empty(unloaded),"unloaded chunks never count as empty ocean");
            player.setPos(point.x,point.y+2,point.z-12);face(point);moveShip(new Vector3d(initial));
        }
        if(ticks==95){check(!FleetObservationNative.empty(remembered),"returning physical hull prevents erroneous removal");System.out.println("FLEET_OBSERVATION_NATIVE_PASS checks="+checks);server.halt(false);server=null;}
    }catch(Throwable error){System.out.println("FLEET_OBSERVATION_NATIVE_FAIL tick="+ticks);error.printStackTrace();server.halt(false);server=null;}}
}
