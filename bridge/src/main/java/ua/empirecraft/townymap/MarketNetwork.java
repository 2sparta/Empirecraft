package ua.empirecraft.townymap;

import com.google.gson.Gson;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.*;
import java.util.function.Supplier;

/** Optional native market channel, confined to the server thread and authenticated packet sender. */
@Mod.EventBusSubscriber(modid=EmpireTownyMap.MOD_ID)
public final class MarketNetwork {
    private static final Gson JSON=new Gson();private static int ticks;
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("empirecraft","market"),()->"1",v->v.equals("1")||v.equals(NetworkRegistry.ABSENT),v->v.equals("1")||v.equals(NetworkRegistry.ABSENT));
    public static void init(){
        CHANNEL.registerMessage(0,Request.class,Request::encode,Request::decode,MarketNetwork::request,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Reply.class,Reply::encode,Reply::decode,MarketNetwork::reply,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public record Request(String session,String action,String section,String form,List<String> values,String token,int page,String screen){
        static void encode(Request p,FriendlyByteBuf b){b.writeUtf(p.session,40);b.writeUtf(p.action,20);b.writeUtf(p.section,12);b.writeUtf(p.form,40);b.writeInt(p.values.size());for(String value:p.values)b.writeUtf(value,4000);b.writeUtf(p.token,40);b.writeInt(p.page);b.writeUtf(p.screen,60);}
        static Request decode(FriendlyByteBuf b){String session=b.readUtf(40),action=b.readUtf(20),section=b.readUtf(12),form=b.readUtf(40);int count=b.readInt();if(count<0||count>20)throw new IllegalArgumentException("Too many market fields");var values=new ArrayList<String>();int total=0;for(int i=0;i<count;i++){String s=b.readUtf(4000);total+=s.length();if(total>8000)throw new IllegalArgumentException("Oversized market form");values.add(s);}return new Request(session,action,section,form,List.copyOf(values),b.readUtf(40),b.readInt(),b.readUtf(60));}
    }
    public record Reply(String json,boolean open,String screen){
        static void encode(Reply p,FriendlyByteBuf b){b.writeUtf(p.json,131072);b.writeBoolean(p.open);b.writeUtf(p.screen,60);}
        static Reply decode(FriendlyByteBuf b){return new Reply(b.readUtf(131072),b.readBoolean(),b.readUtf(60));}
    }
    static Object invoke(Object plugin,UUID player,Request r)throws ReflectiveOperationException {
        return plugin.getClass().getMethod("marketUi",UUID.class,String.class,String.class,String.class,String.class,List.class,String.class,int.class)
            .invoke(plugin,player,r.session,r.action,r.section,r.form,r.values,r.token,r.page);
    }
    static void send(ServerPlayer player,Object data,boolean open,String screen){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Reply(JSON.toJson(data),open,screen));}
    static void request(Request packet,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();var sender=ctx.getSender();if(sender!=null)ctx.enqueueWork(()->{
            try{Object plugin=CurrencyNetwork.plugin();if(plugin==null)throw new IllegalStateException("Market plugin unavailable");send(sender,invoke(plugin,sender.getUUID(),packet),false,packet.screen);}
            catch(Exception ex){send(sender,Map.of("error","Сесію ринку завершено або дія недоступна. Відкрийте /market menu повторно."),false,packet.screen);EmpireTownyMap.LOG.debug("Market request rejected",ex);}
        });ctx.setPacketHandled(true);
    }
    static void reply(Reply packet,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ua.empirecraft.townymap.client.MarketScreen.receive(packet)));ctx.setPacketHandled(true);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||++ticks%5!=0)return;var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        try{Object plugin=CurrencyNetwork.plugin();if(plugin==null)return;for(Object raw:(List<?>)plugin.getClass().getMethod("marketPoll").invoke(plugin)){
            Map<?,?> open=(Map<?,?>)raw;var player=server.getPlayerList().getPlayer(UUID.fromString(open.get("player").toString()));if(player==null||((Number)open.get("expires")).longValue()<System.currentTimeMillis())continue;
            if(!CHANNEL.isRemotePresent(player.connection.connection)){player.sendSystemMessage(Component.literal("Для екрана ринку встановіть міст Empirecraft 1.6.0 на клієнті й сервері."));continue;}
            var request=new Request(open.get("session").toString(),"view","goods","",List.of(),"",1,"");send(player,invoke(plugin,player.getUUID(),request),true,"");
        }}catch(ClassNotFoundException|NoSuchMethodException ignored){/* Optional older Bukkit plugin. */}catch(Exception ex){if(ticks%200==0)EmpireTownyMap.LOG.warn("Market bridge is waiting for Bukkit",ex);}
    }
}
