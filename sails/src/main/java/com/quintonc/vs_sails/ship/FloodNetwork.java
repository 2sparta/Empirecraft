package com.quintonc.vs_sails.ship;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Bounded client-only packets, sent on topology changes and when a player approaches a ship. */
public final class FloodNetwork {
    private static SimpleChannel channel;
    private record Mask(String dimension,long ship,FloodZones.Region region) {}
    private FloodNetwork(){}
    public static void init(){
        String version="15";
        channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("vs_sails","flooding"),()->version,version::equals,version::equals);
        channel.registerMessage(0,Mask.class,FloodNetwork::write,FloodNetwork::read,(packet,context)->{
            var ctx=context.get();ctx.enqueueWork(()->FloodZones.accept(packet.dimension,packet.ship,packet.region));ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(ServerPlayer player,String dimension,long ship,FloodZones.Region region){
        if(channel!=null)channel.send(PacketDistributor.PLAYER.with(()->player),new Mask(dimension,ship,region));
    }
    private static void write(Mask packet,FriendlyByteBuf b){
        b.writeUtf(packet.dimension,256);b.writeLong(packet.ship);b.writeBoolean(packet.region!=null);
        if(packet.region!=null){var r=packet.region;b.writeInt(r.x);b.writeInt(r.y);b.writeInt(r.z);b.writeVarInt(r.sx);b.writeVarInt(r.sy);b.writeVarInt(r.sz);b.writeByteArray(r.bytes());}
    }
    private static Mask read(FriendlyByteBuf b){
        String dimension=b.readUtf(256);long ship=b.readLong();FloodZones.Region r=null;
        if(b.readBoolean())r=new FloodZones.Region(b.readInt(),b.readInt(),b.readInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readByteArray(FloodTopology.MAX_SCAN/8+1));
        return new Mask(dimension,ship,r);
    }
}
