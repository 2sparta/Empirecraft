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

/** Coordinates/images originate on the server. The only C2S request is a rate-limited optical save. */
public final class StrategyNetwork {
    private static long batch;
    private static final SpyglassService SERVICE=new SpyglassService();
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("empirecraft","strategy_map"),
            ()->"2",v->v.equals("2")||v.equals(NetworkRegistry.ABSENT),v->v.equals("2")||v.equals(NetworkRegistry.ABSENT));
    private static final class Bounds {static void page(String json,int part,int total){if(json==null||json.length()>300000||part<0||total<1||total>8||part>=total)throw new IllegalArgumentException("Invalid ship page");}}
    public record Observations(String json,long focus,long batch,int part,int total){
        public Observations{Bounds.page(json,part,total);}
        static void encode(Observations p,FriendlyByteBuf b){b.writeUtf(p.json,300000);b.writeLong(p.focus);b.writeLong(p.batch);b.writeByte(p.part);b.writeByte(p.total);}
        static Observations decode(FriendlyByteBuf b){return new Observations(b.readUtf(300000),b.readLong(),b.readLong(),b.readUnsignedByte(),b.readUnsignedByte());}
    }
    public record Contacts(String json,long focus,long batch,int part,int total){
        public Contacts{Bounds.page(json,part,total);}
        static void encode(Contacts p,FriendlyByteBuf b){Observations.encode(new Observations(p.json,p.focus,p.batch,p.part,p.total),b);}
        static Contacts decode(FriendlyByteBuf b){var p=Observations.decode(b);return new Contacts(p.json,p.focus,p.batch,p.part,p.total);}
    }
    public record Save(long ship){public Save{if(ship<0)throw new IllegalArgumentException("Invalid sighting target");}}
    public record IconRequest(long ship){public IconRequest{if(ship<0)throw new IllegalArgumentException("Invalid icon target");}}
    public record Icon(long ship,ShipIcon image){
        public Icon{if(ship<0||image==null)throw new IllegalArgumentException("Invalid ship icon");}
        static void encode(Icon p,FriendlyByteBuf b){b.writeLong(p.ship);for(int pixel:p.image.pixels())b.writeInt(pixel);}
        static Icon decode(FriendlyByteBuf b){long id=b.readLong();int[] pixels=new int[ShipIcon.SIDE*ShipIcon.SIDE];for(int i=0;i<pixels.length;i++)pixels[i]=b.readInt();return new Icon(id,new ShipIcon(pixels));}
    }
    static void init(){
        CHANNEL.registerMessage(0,Observations.class,Observations::encode,Observations::decode,StrategyNetwork::receive,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Contacts.class,Contacts::encode,Contacts::decode,StrategyNetwork::receiveContacts,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2,Save.class,(p,b)->b.writeLong(p.ship),b->new Save(b.readLong()),(p,s)->{
            var context=s.get();context.enqueueWork(()->{var player=context.getSender();if(player!=null)SERVICE.save(player,p.ship);});context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3,Icon.class,Icon::encode,Icon::decode,(p,s)->{
            var context=s.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ua.empirecraft.townymap.client.ShipReconClient.icon(p.ship,p.image,context.getNetworkManager())));context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4,IconRequest.class,(p,b)->b.writeLong(p.ship),b->new IconRequest(b.readLong()),(p,s)->{
            var context=s.get();context.enqueueWork(()->{var player=context.getSender();if(player!=null)SERVICE.requestIcon(player,p.ship);});context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(SERVICE);
    }
    static boolean present(ServerPlayer player){return CHANNEL.isRemotePresent(player.connection.connection);}
    private static void pages(ServerPlayer player,com.google.gson.JsonArray rows,long focus,boolean live){
        if(!present(player))return;if(rows.size()>ShipObservation.Log.LIMIT)throw new IllegalArgumentException("Oversized ship view");
        int total=Math.max(1,(rows.size()+15)/16);long sequence=++batch;
        for(int part=0;part<total;part++){
            var page=new com.google.gson.JsonArray();for(int i=part*16;i<Math.min(rows.size(),(part+1)*16);i++){
                var row=rows.get(i).getAsJsonObject();if(live||row.toString().length()>18000){row=row.deepCopy();row.addProperty("flag","{}");}page.add(row);
            }
            Object packet=live?new Contacts(page.toString(),focus,sequence,part,total):new Observations(page.toString(),focus,sequence,part,total);
            CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);
        }
    }
    static void send(ServerPlayer player,String json,long focus){pages(player,com.google.gson.JsonParser.parseString(json).getAsJsonArray(),focus,false);}
    static void contacts(ServerPlayer player,List<ShipObservation> rows,long focus){pages(player,new com.google.gson.Gson().toJsonTree(rows).getAsJsonArray(),focus,true);}
    static void icon(ServerPlayer player,long ship,ShipIcon icon){if(present(player))CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Icon(ship,icon));}
    private static void receive(Observations p,Supplier<NetworkEvent.Context> s){var c=s.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ua.empirecraft.townymap.client.StrategyMapUi.observations(p.json,p.focus,p.batch,p.part,p.total,c.getNetworkManager())));c.setPacketHandled(true);}
    private static void receiveContacts(Contacts p,Supplier<NetworkEvent.Context> s){var c=s.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ua.empirecraft.townymap.client.ShipReconClient.contacts(p.json,p.focus,p.batch,p.part,p.total,c.getNetworkManager())));c.setPacketHandled(true);}
}
