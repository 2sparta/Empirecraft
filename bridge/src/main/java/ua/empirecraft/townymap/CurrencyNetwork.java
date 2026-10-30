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
    private static final String VERSION="5";
    private static final Gson JSON=new Gson();
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("empirecraft","currency"),()->VERSION,v->VERSION.equals(v)||NetworkRegistry.ABSENT.equals(v),v->VERSION.equals(v)||NetworkRegistry.ABSENT.equals(v));
    private static long ticks;
    public static void init(){
        CHANNEL.registerMessage(0,Request.class,Request::encode,Request::decode,CurrencyNetwork::request,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Reply.class,Reply::encode,Reply::decode,CurrencyNetwork::reply,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2,MapSelection.class,MapSelection::encode,MapSelection::decode,CurrencyNetwork::mapSelection,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3,MapState.class,MapState::encode,MapState::decode,CurrencyNetwork::mapState,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4,MapQuery.class,(p,b)->{},b->new MapQuery(),CurrencyNetwork::mapQuery,Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public record MapSelection(MapMode mode){
        static void encode(MapSelection p,FriendlyByteBuf b){b.writeEnum(p.mode);}
        static MapSelection decode(FriendlyByteBuf b){return new MapSelection(b.readEnum(MapMode.class));}
    }
    static void mapSelection(MapSelection packet,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();var player=context.getSender();
        if(player!=null)context.enqueueWork(()->{BridgeService.select(player.getUUID(),packet.mode());sendMode(player);});
        context.setPacketHandled(true);
    }
    public record MapQuery(){}
    public record MapState(MapMode mode){
        static void encode(MapState p,FriendlyByteBuf b){b.writeEnum(p.mode);}
        static MapState decode(FriendlyByteBuf b){return new MapState(b.readEnum(MapMode.class));}
    }
    static void sendMode(ServerPlayer player){
        if(CHANNEL.isRemotePresent(player.connection.connection))CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new MapState(BridgeService.selected(player.getUUID())));
    }
    static void mapQuery(MapQuery packet,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();var player=context.getSender();if(player!=null)context.enqueueWork(()->sendMode(player));context.setPacketHandled(true);
    }
    static void mapState(MapState packet,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ua.empirecraft.townymap.client.MapModeButton.selected(packet.mode())));context.setPacketHandled(true);
    }
    @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent event){
        var command=net.minecraft.commands.Commands.literal("tmap");
        for(MapMode mode:MapMode.values())command.then(net.minecraft.commands.Commands.literal(mode.name().toLowerCase(Locale.ROOT)).executes(ctx->{
            var player=ctx.getSource().getPlayerOrException();BridgeService.select(player.getUUID(),mode);sendMode(player);
            ctx.getSource().sendSuccess(()->Component.literal("Мапа: "+mode.label),false);return 1;
        }));
        event.getDispatcher().register(command);
    }
    public record Request(String action,String from,String to,String amount,String minimum,String settings,String period,String token,String screen){
        static void encode(Request p,FriendlyByteBuf b){b.writeUtf(p.action,20);b.writeUtf(p.from,8);b.writeUtf(p.to,8);b.writeUtf(p.amount,32);b.writeUtf(p.minimum,32);b.writeUtf(p.settings,256);b.writeUtf(p.period,3);b.writeUtf(p.token,40);b.writeUtf(p.screen,40);}
        static Request decode(FriendlyByteBuf b){return new Request(b.readUtf(20),b.readUtf(8),b.readUtf(8),b.readUtf(32),b.readUtf(32),b.readUtf(256),b.readUtf(3),b.readUtf(40),b.readUtf(40));}
    }
    public record Reply(String json,boolean open,String mode,String screen){
        static void encode(Reply p,FriendlyByteBuf b){b.writeUtf(p.json,131072);b.writeBoolean(p.open);b.writeUtf(p.mode,10);b.writeUtf(p.screen,40);}
        static Reply decode(FriendlyByteBuf b){return new Reply(b.readUtf(131072),b.readBoolean(),b.readUtf(10),b.readUtf(40));}
    }
    static Object plugin()throws ReflectiveOperationException{
        Class<?> bukkit=Class.forName("org.bukkit.Bukkit");if(bukkit.getMethod("getServer").invoke(null)==null)return null;
        Object manager=bukkit.getMethod("getPluginManager").invoke(null);if(manager==null)return null;
        Object plugin=Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin",String.class).invoke(manager,"EmpirecraftFeudal");
        if(plugin==null || !(boolean)Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(plugin))return null;return plugin;
    }
    static Object invoke(Object plugin,UUID player,Request r)throws ReflectiveOperationException{
        return plugin.getClass().getMethod("currencyUi",UUID.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class).invoke(plugin,player,r.action,r.from,r.to,r.amount,r.minimum,r.settings,r.period,r.token);
    }
    static void send(ServerPlayer player,Object data,boolean open,String mode,String screen){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Reply(JSON.toJson(data),open,mode,screen));}
    static void request(Request packet,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();ServerPlayer sender=ctx.getSender();
        if(sender!=null)ctx.enqueueWork(()->{
            try{Object plugin=plugin();if(plugin==null)throw new IllegalStateException("Плагін валют недоступний.");send(sender,invoke(plugin,sender.getUUID(),packet),false,"",packet.screen);}
            catch(Exception ex){send(sender,Map.of("error","Валютний сервіс недоступний. Повідомте адміністратора."),false,"",packet.screen);EmpireTownyMap.LOG.warn("Currency request failed",ex);}
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
                if(!CHANNEL.isRemotePresent(player.connection.connection)){player.sendSystemMessage(Component.literal("Оновіть клієнтський міст Empirecraft до 1.4.0 для екрана валюти."));continue;}
                var request=new Request("view",open.get("from").toString(),open.get("to").toString(),"","","","1d","","");send(player,invoke(plugin,player.getUUID(),request),true,open.get("mode").toString(),"");
            }
        }catch(ClassNotFoundException|NoSuchMethodException ignored){/* Plain Forge or an older optional plugin has no currency UI. */}
        catch(Exception ex){if(ticks%200==0)EmpireTownyMap.LOG.warn("Currency screen bridge is waiting for Bukkit",ex);}
    }
}


