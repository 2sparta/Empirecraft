package ua.empirecraft.kitchenclienttest;
import net.fretux.knockedback.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.*;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.EventPriority;
@Mod("empirecraft_kitchen_client_probe")
public class ClientProbe {
 int ticks,phase;boolean loaded,done;RemotePlayer wounded;
 public ClientProbe(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick);}
 void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),c->{});}
 void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||done)return;try{
  var mc=Minecraft.getInstance();
  if(!loaded&&mc.screen!=null&&mc.screen.getClass().getName().endsWith("EmpireHome")){loaded=true;mc.createWorldOpenFlows().loadLevel(mc.screen,"KITCHEN-TEST");return;}
  if(loaded&&mc.level==null&&(mc.screen instanceof ConfirmScreen||mc.screen instanceof BackupConfirmScreen)){for(var w:mc.screen.children())if(w instanceof net.minecraft.client.gui.components.Button b){b.onPress();break;}}
  if(mc.level==null||mc.player==null)return;
  if(++ticks<100)return;
  if(phase==0){
   mc.setScreen(null);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
   mc.getSingleplayerServer().execute(()->KnockedManager.applyKnockedState(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())));
   phase=1;ticks=0;return;
  }
  if(phase==1){
   check(mc.player.getForcedPose()==Pose.SWIMMING,"Client sees downed pose");
   var animation=dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry.getAnimation(new net.minecraft.resources.ResourceLocation("knockedback","animation.model.wounded_breath"));check(animation!=null&&animation.isInfinite,"Breathing resource registered and loops");
   var f=ClientTriage.class.getDeclaredField("layers");f.setAccessible(true);var layers=(java.util.Map)f.get(null);check(layers.containsKey(mc.player.getUUID()),"Breathing layer attached");
   capture("01-wounded-breathing");
   mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());KnockedManager.removeKnockedState(p);Triage.recovered(p);});phase=2;ticks=0;return;
  }
  if(phase==2){check(ClientTriage.recovering(mc.player.getUUID()),"Recovery sync received");mc.player.setSprinting(true);check(!mc.player.isSprinting(),"Client mixin blocks sprint");capture("02-recovery");
   mc.options.setCameraType(CameraType.FIRST_PERSON);
   wounded=new RemotePlayer(mc.level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"WoundedScout"));wounded.setPos(mc.player.getX(),mc.player.getY(),mc.player.getZ()+2.5);mc.level.addPlayer(900001,wounded);phase=3;ticks=0;return;
  }
  if(phase==3){ClientTriage.accept(new Triage.State(wounded.getUUID(),true,0));mc.player.setYRot(0);mc.player.setXRot(27);if(ticks<125)return;
   check(wounded.getForcedPose()==Pose.SWIMMING,"Remote downed pose sync");check(mc.hitResult instanceof EntityHitResult hit&&hit.getEntity()==wounded,"Crosshair points at actual wounded entity");capture("03-wounded-indicator");phase=4;ticks=0;return;
  }
  check(mc.player.getForcedPose()!=Pose.SWIMMING,"Rescue clears own downed pose");done=true;System.out.println("KITCHEN_CLIENT_PASS knocked=true animation=true recovery=true sprintBlocked=true remoteIndicator=true");
 }catch(Throwable t){done=true;System.out.println("KITCHEN_CLIENT_FAIL "+t);t.printStackTrace();}}
}
