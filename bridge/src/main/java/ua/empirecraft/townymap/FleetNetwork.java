package ua.empirecraft.townymap;

import com.google.gson.Gson;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Sender-bound fleet forms and personal sightings; clients never receive the live fleet registry. */
@Mod.EventBusSubscriber(modid=EmpireTownyMap.MOD_ID)
public final class FleetNetwork {
    private static final Gson JSON=new Gson();private static int ticks;
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("empirecraft","fleet"),()->"1",v->v.equals("1")||v.equals(NetworkRegistry.ABSENT),v->v.equals("1")||v.equals(NetworkRegistry.ABSENT));
    public record Request(String session,String action,String section,String form,List<String> values,String token,int page,String screen){
        static void encode(Request p,FriendlyByteBuf b){b.writeUtf(p.session,40);b.writeUtf(p.action,24);b.writeUtf(p.section,16);b.writeUtf(p.form,24);b.writeVarInt(p.values.size());for(String v:p.values)b.writeUtf(v,2000);b.writeUtf(p.token,40);b.writeVarInt(p.page);b.writeUtf(p.screen,60);}
        static Request decode(FriendlyByteBuf b){String session=b.readUtf(40),action=b.readUtf(24),section=b.readUtf(16),form=b.readUtf(24);int count=b.readVarInt();if(count<0||count>10)throw new IllegalArgumentException("Too many fleet fields");List<String> values=new ArrayList<>();int length=0;for(int i=0;i<count;i++){String v=b.readUtf(2000);length+=v.length();if(length>8000)throw new IllegalArgumentException("Oversized fleet form");values.add(v);}return new Request(session,action,section,form,List.copyOf(values),b.readUtf(40),b.readVarInt(),b.readUtf(60));}
    }
    public record Reply(String json,boolean open,String screen){
        static void encode(Reply p,FriendlyByteBuf b){b.writeUtf(p.json,131072);b.writeBoolean(p.open);b.writeUtf(p.screen,60);}
        static Reply decode(FriendlyByteBuf b){return new Reply(b.readUtf(131072),b.readBoolean(),b.readUtf(60));}
    }
    public static void init(){CHANNEL.registerMessage(0,Request.class,Request::encode,Request::decode,FleetNetwork::request,Optional.of(NetworkDirection.PLAY_TO_SERVER));CHANNEL.registerMessage(1,Reply.class,Reply::encode,Reply::decode,FleetNetwork::reply,Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
    private static Object ui(Object plugin,UUID player,Request r)throws Exception {return plugin.getClass().getMethod("fleetUi",UUID.class,String.class,String.class,String.class,String.class,List.class,String.class,int.class).invoke(plugin,player,r.session,r.action,r.section,r.form,r.values,r.token,r.page);}
    private static void send(net.minecraft.server.level.ServerPlayer player,Object data,boolean open,String screen){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Reply(JSON.toJson(data),open,screen));}
    private static void request(Request p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();var player=ctx.getSender();if(player!=null)ctx.enqueueWork(()->{
        try{Object plugin=CurrencyNetwork.plugin();if(plugin==null)throw new IllegalStateException("Fleet unavailable");
            if(p.action.equals("view-fov")){
                if(p.values.size()!=2)return;double h=Double.parseDouble(p.values.get(0)),v=Double.parseDouble(p.values.get(1));if(!Double.isFinite(h)||!Double.isFinite(v))return;
                plugin.getClass().getMethod("fleetRequest",String.class,Map.class).invoke(plugin,"view",Map.of("player",player.getUUID().toString(),"horizontal",h,"vertical",v));return;
            }
            if(p.action.equals("map-hide")){
                if(p.values.size()!=1)return;plugin.getClass().getMethod("fleetRequest",String.class,Map.class).invoke(plugin,"hide",Map.of("player",player.getUUID().toString(),"ship",Long.parseLong(p.values.get(0))));return;
            }
            send(player,ui(plugin,player.getUUID(),p),false,p.screen);
        }catch(Exception ex){send(player,Map.of("error","Флот недоступний. Відкрийте /fleet повторно."),false,p.screen);EmpireTownyMap.LOG.debug("Fleet request rejected",ex);}
    });ctx.setPacketHandled(true);}
    private static void reply(Reply p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ua.empirecraft.townymap.client.FleetScreen.receive(p)));ctx.setPacketHandled(true);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END || ++ticks%10!=0)return;var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        try{Object plugin=CurrencyNetwork.plugin();if(plugin==null)return;
            for(Object raw:(List<?>)plugin.getClass().getMethod("fleetPoll").invoke(plugin)){
                var entry=(Map<?,?>)raw;var player=server.getPlayerList().getPlayer(UUID.fromString(entry.get("player").toString()));if(player==null)continue;
                if(!CHANNEL.isRemotePresent(player.connection.connection)){player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Для меню флоту встановіть міст Empirecraft 1.11.0 на клієнті й сервері."));continue;}
                var req=new Request(entry.get("session").toString(),"view","","",List.of(),"",1,"");send(player,ui(plugin,player.getUUID(),req),true,"");
            }
            if(ticks%20==0)for(var player:server.getPlayerList().getPlayers())if(CHANNEL.isRemotePresent(player.connection.connection)){
                Object marks=plugin.getClass().getMethod("fleetMarks",UUID.class).invoke(plugin,player.getUUID());send(player,Map.of("marks",marks),false,"map");
            }
        }catch(ClassNotFoundException|NoSuchMethodException absent){/* Optional older server plugin. */}catch(Exception ex){if(ticks%200==0)EmpireTownyMap.LOG.warn("Fleet bridge waiting for Bukkit",ex);}
    }
}
