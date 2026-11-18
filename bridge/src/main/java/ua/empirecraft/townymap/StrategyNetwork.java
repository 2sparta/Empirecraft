package ua.empirecraft.townymap;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-to-client only: clients cannot submit targets, ownership, coordinates or velocity. */
public final class StrategyNetwork {
    private static long batch;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("empirecraft","strategy_map"),
            ()->"1",v->v.equals("1")||v.equals(NetworkRegistry.ABSENT),v->v.equals("1")||v.equals(NetworkRegistry.ABSENT));
    public record Observations(String json,long focus,long batch,int part,int total) {
        public Observations {
            if (json==null || json.length()>300000 || part<0 || total<1 || total>8 || part>=total)
                throw new IllegalArgumentException("Invalid ship observation page");
        }
        static void encode(Observations p,FriendlyByteBuf buffer) { buffer.writeUtf(p.json,300000);buffer.writeLong(p.focus);buffer.writeLong(p.batch);buffer.writeByte(p.part);buffer.writeByte(p.total); }
        static Observations decode(FriendlyByteBuf buffer) { return new Observations(buffer.readUtf(300000),buffer.readLong(),buffer.readLong(),buffer.readUnsignedByte(),buffer.readUnsignedByte()); }
    }
    static void init() {
        CHANNEL.registerMessage(0,Observations.class,Observations::encode,Observations::decode,StrategyNetwork::receive,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new SpyglassService());
    }
    static void send(ServerPlayer player,String json,long focus) {
        if(!CHANNEL.isRemotePresent(player.connection.connection))return;
        var rows=com.google.gson.JsonParser.parseString(json).getAsJsonArray();
        if(rows.size()>ShipObservation.Log.LIMIT)throw new IllegalArgumentException("Oversized ship log");
        int total=Math.max(1,(rows.size()+15)/16);long sequence=++batch;
        for(int part=0;part<total;part++) {
            var page=new com.google.gson.JsonArray();for(int i=part*16;i<Math.min(rows.size(),(part+1)*16);i++) {
                var row=rows.get(i).getAsJsonObject();
                if(row.toString().length()>18000){row=row.deepCopy();row.addProperty("flag","{}");}
                page.add(row);
            }
            CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Observations(page.toString(),focus,sequence,part,total));
        }
    }
    private static void receive(Observations p,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                ()->()->ua.empirecraft.townymap.client.StrategyMapUi.observations(p.json,p.focus,p.batch,p.part,p.total,context.getNetworkManager())));context.setPacketHandled(true);
    }
}
