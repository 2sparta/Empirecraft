package ua.empirecraft.knockedtest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.network.*;import net.minecraft.network.protocol.*;
import net.minecraft.server.level.*;import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.fretux.knockedback.*;
@Mod("empirecraft_knocked_probe") public class Probe {
 net.minecraft.server.MinecraftServer server; ServerLevel level; int ticks,checks;
 void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public Probe(){MinecraftForge.EVENT_BUS.addListener(this::start);MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void start(ServerStartedEvent e){server=e.getServer();level=server.overworld();}
 void tick(TickEvent.ServerTickEvent e){if(server!=null&&e.phase==TickEvent.Phase.END&&++ticks==25)verify();}
    private ServerPlayer player(String name) throws Exception {
        var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),name));
        var channel=new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelOutboundHandlerAdapter(){
            @Override public void write(io.netty.channel.ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise){io.netty.util.ReferenceCountUtil.release(message);promise.trySuccess();}
            @Override public void flush(io.netty.channel.ChannelHandlerContext context){}
        });
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=channel;
        channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.PLAY);
        player.connection=new ServerGamePacketListenerImpl(server,connection,player);
        var spawnImmunity=ServerPlayer.class.getDeclaredField("f_8921_");spawnImmunity.setAccessible(true);spawnImmunity.setInt(player,0);
        player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);player.moveTo(0,90,0);level.addNewPlayer(player);
        for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
            field.setAccessible(true);
            if(field.getType()==List.class)try {((List)field.get(server.getPlayerList())).add(player);break;}catch(UnsupportedOperationException ignored){}
        }
        for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(Map.class.isAssignableFrom(field.getType())&&field.getGenericType().getTypeName().contains("java.util.UUID")&&field.getGenericType().getTypeName().contains("ServerPlayer")) {
            field.setAccessible(true);((Map)field.get(server.getPlayerList())).put(player.getUUID(),player);
        }
        return player;
    }


 void verify(){try{
 var f=KnockedManager.class.getDeclaredField("knockedEntities");f.setAccessible(true);var states=(Map<UUID,Integer>)f.get(null);
 int min=2400,max=0;
 for(int i=0;i<10000;i++){int n=KnockedManager.getKnockedDuration();check(n>=1000&&n<=2400,"50–120 second range");min=Math.min(min,n);max=Math.max(max,n);}check(min<1100&&max>2300,"Range spans both ends");
 check(!BleedoutPolicy.heartbeat(301)&&BleedoutPolicy.heartbeat(300),"Heartbeat begins at 15 seconds");
 check(BleedoutPolicy.darkness(201)==0&&BleedoutPolicy.darkness(180)==.5f&&BleedoutPolicy.darkness(160)==1&&BleedoutPolicy.darkness(1)==1&&BleedoutPolicy.darkness(0)==0,"10 second fade; final 8 seconds black; reset");
 var a=player("BleedTest");KnockedManager.applyKnockedState(a);check(KnockedManager.isKnocked(a),"Native down state entered");
 states.put(a.getUUID(),400);
 var hit=new net.minecraftforge.event.entity.living.LivingHurtEvent(a,a.damageSources().generic(),1);KnockedManager.onLivingHurt(hit);check(states.get(a.getUUID())==400&&hit.isCanceled(),"Hits do not reset deadline");
 KnockedManager.setGripped(a,true);KnockedManager.tickKnockedStates();check(states.get(a.getUUID())==399,"Gripped timer continues");KnockedManager.setGripped(a,false);
 var b=player("CarrierTest");
 var start=CarryManager.class.getDeclaredMethod("startCarry",ServerPlayer.class,net.minecraft.world.entity.player.Player.class);start.setAccessible(true);start.invoke(null,b,a);
 check(CarryManager.isBeingCarried(a.getUUID()),"Native carrying started");
 states.put(a.getUUID(),2);KnockedManager.tickKnockedStates();check(states.get(a.getUUID())==1,"Carried timer continues");
 KnockedManager.tickKnockedStates();check(!a.isAlive()&&!KnockedManager.isKnocked(a)&&!CarryManager.isBeingCarried(a.getUUID()),"Deadline kills while carried and cleans state");
 var c=player("RescueTest");KnockedManager.applyKnockedState(c);
 var complete=HelpUpHandler.class.getDeclaredMethod("completeHelpUp",ServerPlayer.class,ServerPlayer.class);complete.setAccessible(true);complete.invoke(null,b,c);
 check(c.isAlive()&&!KnockedManager.isKnocked(c)&&c.getHealth()>1,"Native rescue cancels bleedout");KnockedManager.tickKnockedStates();check(c.isAlive(),"Rescue remains alive after ticks");
 var d=player("LogoutTest");KnockedManager.applyKnockedState(d);KnockedManager.onPlayerLoggedOut(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(d));check(!d.isAlive()&&!KnockedManager.isKnocked(d),"Logout cannot reset timer / save a free revive");
 System.out.println("KNOCKED_NATIVE_PASS checks="+checks);
 }catch(Throwable t){System.out.println("KNOCKED_NATIVE_FAIL "+t);t.printStackTrace();}finally{server.halt(false);}}
}
