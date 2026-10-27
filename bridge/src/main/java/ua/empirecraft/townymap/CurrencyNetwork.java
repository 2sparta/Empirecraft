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

/** Dedicated Forge packets, not Bukkit chest inventories or unauthenticated chat actions. */
@Mod.EventBusSubscriber(modid=EmpireTownyMap.MOD_ID)
public final class CurrencyNetwork {
    private static final String VERSION="1";
    private static final Gson JSON=new Gson();
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("empirecraft","currency"),()->VERSION,v->VERSION.equals(v)||NetworkRegistry.ABSENT.equals(v),v->VERSION.equals(v)||NetworkRegistry.ABSENT.equals(v));
    private static long ticks;
    public static void init(){
        CHANNEL.registerMessage(0,Request.class,Request::encode,Request::decode,CurrencyNetwork::request,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Reply.class,Reply::encode,Reply::decode,CurrencyNetwork::reply,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public record Request(String action,String from,String to,String amount,String period,String token){
        static void encode(Request p,FriendlyByteBuf b){b.writeUtf(p.action,20);b.writeUtf(p.from,8);b.writeUtf(p.to,8);b.writeUtf(p.amount,32);b.writeUtf(p.period,3);b.writeUtf(p.token,40);}
        static Request decode(FriendlyByteBuf b){return new Request(b.readUtf(20),b.readUtf(8),b.readUtf(8),b.readUtf(32),b.readUtf(3),b.readUtf(40));}
    }
    public record Reply(String json,boolean open,String mode){
        static void encode(Reply p,FriendlyByteBuf b){b.writeUtf(p.json,131072);b.writeBoolean(p.open);b.writeUtf(p.mode,10);}
        static Reply decode(FriendlyByteBuf b){return new Reply(b.readUtf(131072),b.readBoolean(),b.readUtf(10));}
    }
    static Object plugin()throws ReflectiveOperationException{
        Class<?> bukkit=Class.forName("org.bukkit.Bukkit");if(bukkit.getMethod("getServer").invoke(null)==null)return null;
        Object manager=bukkit.getMethod("getPluginManager").invoke(null);if(manager==null)return null;
        Object plugin=Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin",String.class).invoke(manager,"EmpirecraftFeudal");
        if(plugin==null || !(boolean)Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(plugin))return null;return plugin;
    }
    static Object invoke(Object plugin,UUID player,Request r)throws ReflectiveOperationException{
        return plugin.getClass().getMethod("currencyUi",UUID.class,String.class,String.class,String.class,String.class,String.class,String.class).invoke(plugin,player,r.action,r.from,r.to,r.amount,r.period,r.token);
    }
    static void send(ServerPlayer player,Object data,boolean open,String mode){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Reply(JSON.toJson(data),open,mode));}
    static void request(Request packet,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();ServerPlayer sender=ctx.getSender();
        if(sender!=null)ctx.enqueueWork(()->{
            try{Object plugin=plugin();if(plugin==null)throw new IllegalStateException("Плагін валют недоступний.");send(sender,invoke(plugin,sender.getUUID(),packet),false,"");}
            catch(Exception ex){send(sender,Map.of("error","Валютний сервіс недоступний. Повідомте адміністратора."),false,"");EmpireTownyMap.LOG.warn("Currency request failed",ex);}
        });ctx.setPacketHandled(true);
    }
    static void reply(Reply packet,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ua.empirecraft.townymap.client.CurrencyClient.receive(packet)));ctx.setPacketHandled(true);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END || ++ticks%5!=0)return;var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        try{
            Object plugin=plugin();if(plugin==null)return;
            for(Object raw:(List<?>)plugin.getClass().getMethod("currencyPoll").invoke(plugin)){
                Map<?,?> open=(Map<?,?>)raw;ServerPlayer player=server.getPlayerList().getPlayer(UUID.fromString(open.get("player").toString()));
                if(player==null || ((Number)open.get("expires")).longValue()<System.currentTimeMillis())continue;
                if(!CHANNEL.isRemotePresent(player.connection.connection)){player.sendSystemMessage(Component.literal("Оновіть клієнтський міст Empirecraft до 1.2.0 для екрана валюти."));continue;}
                var request=new Request("view",open.get("from").toString(),open.get("to").toString(),"","1d","");send(player,invoke(plugin,player.getUUID(),request),true,open.get("mode").toString());
            }
        }catch(ClassNotFoundException|NoSuchMethodException ignored){/* Plain Forge or an older optional plugin has no currency UI. */}
        catch(Exception ex){if(ticks%200==0)EmpireTownyMap.LOG.warn("Currency screen bridge is waiting for Bukkit",ex);}
    }
}
