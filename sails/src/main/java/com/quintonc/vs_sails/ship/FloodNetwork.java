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
    private record Mask(String dimension,long ship,FloodZones.Region region,Map<Long,Integer> volumes) {}
    private FloodNetwork(){}
    public static void init(){
        String version="16";
        channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("vs_sails","flooding"),()->version,version::equals,version::equals);
        channel.registerMessage(0,Mask.class,FloodNetwork::write,FloodNetwork::read,(packet,context)->{
            var ctx=context.get();ctx.enqueueWork(()->{
                FloodZones.accept(packet.dimension,packet.ship,packet.region);
                var previous=FloodZones.acceptVolumes(packet.dimension,packet.ship,packet.volumes);
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        ()->()->{
                            com.quintonc.vs_sails.client.FloodWaterRenderer.mask(packet.ship,packet.region);
                            com.quintonc.vs_sails.client.FloodWaterRenderer.volumes(previous,packet.volumes);
                        });
            });ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(ServerPlayer player,String dimension,long ship,FloodZones.Region region){
        send(player,dimension,ship,region,Map.of());
    }
    public static void send(ServerPlayer player,String dimension,long ship,FloodZones.Region region,Map<Long,Integer> volumes){
        if(channel!=null)channel.send(PacketDistributor.PLAYER.with(()->player),new Mask(dimension,ship,region,Map.copyOf(volumes)));
    }
    private static void write(Mask packet,FriendlyByteBuf b){
        b.writeUtf(packet.dimension,256);b.writeLong(packet.ship);b.writeBoolean(packet.region!=null);
        if(packet.region!=null){var r=packet.region;b.writeInt(r.x);b.writeInt(r.y);b.writeInt(r.z);b.writeVarInt(r.sx);b.writeVarInt(r.sy);b.writeVarInt(r.sz);b.writeByteArray(r.bytes());}
        b.writeVarInt(packet.volumes.size());for(var e:packet.volumes.entrySet()){b.writeLong(e.getKey());b.writeByte(e.getValue());}
    }
    private static Mask read(FriendlyByteBuf b){
        String dimension=b.readUtf(256);long ship=b.readLong();FloodZones.Region r=null;
        if(b.readBoolean())r=new FloodZones.Region(b.readInt(),b.readInt(),b.readInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readByteArray(FloodTopology.MAX_SCAN/8+1));
        int count=b.readVarInt();if(count<0 || count>FloodModel.MAX_CELLS)throw new IllegalArgumentException("Invalid flood volume packet");
        Map<Long,Integer> volumes=new HashMap<>();for(int i=0;i<count;i++){long pos=b.readLong();int q=b.readUnsignedByte();if(q<1 || q>8)throw new IllegalArgumentException("Invalid finite water measure");volumes.put(pos,q);}
        return new Mask(dimension,ship,r,Map.copyOf(volumes));
    }
}
