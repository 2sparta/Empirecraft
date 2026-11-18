package net.fretux.knockedback;
import java.util.*;
import dev.kosmx.playerAnim.api.layered.*;
import dev.kosmx.playerAnim.minecraftApi.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="knockedback",value=Dist.CLIENT)
public final class ClientTriage {
 private record View(boolean down,int recovery,long received){}
 private static final Map<UUID,View> states=new HashMap<>();
 private static final Map<UUID,ModifierLayer<IAnimation>> layers=new HashMap<>();
 private static Object world;
 private static final Set<UUID> forced=new HashSet<>();
 public static void accept(Triage.State p){var mc=Minecraft.getInstance();if(mc.level!=null){if(world!=mc.level){clear();world=mc.level;}states.put(p.id(),new View(p.down(),p.recovery(),mc.level.getGameTime()));}}
 public static boolean recovering(UUID id){var v=view(id);var mc=Minecraft.getInstance();return v!=null&&v.recovery()-(mc.level.getGameTime()-v.received())>0;}
 private static void clear(){layers.values().forEach(l->l.setAnimation(null));layers.clear();states.clear();forced.clear();}
 private static View view(UUID id){var mc=Minecraft.getInstance();var v=states.get(id);return mc.level!=null&&v!=null&&mc.level.getGameTime()-v.received()<60?v:null;}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ClientTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
  if(mc.level==null||mc.player==null){clear();world=null;return;}
  if(world!=mc.level){clear();world=mc.level;}
  for(AbstractClientPlayer p:mc.level.players()){
   var v=view(p.getUUID());boolean down=v!=null&&v.down();
   if(down&&!p.isPassenger()){p.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);forced.add(p.getUUID());}
   else if(forced.remove(p.getUUID())&&p.getForcedPose()==net.minecraft.world.entity.Pose.SWIMMING)p.setForcedPose(null);
   var layer=layers.get(p.getUUID());
   if(down){
    if(layer==null){layer=new ModifierLayer<>();layers.put(p.getUUID(),layer);PlayerAnimationAccess.getPlayerAnimLayer(p).addAnimLayer(5,layer);}
    if(!layer.isActive()){var animation=PlayerAnimationRegistry.getAnimation(new ResourceLocation("knockedback","animation.model.wounded_breath"));if(animation!=null)layer.setAnimation(new KeyframeAnimationPlayer(animation));}
   }else if(layer!=null)layer.setAnimation(null);
   if(p==mc.player&&v!=null&&v.recovery()-(mc.level.getGameTime()-v.received())>0)p.setSprinting(false);
  }
  var ids=new HashSet<UUID>();mc.level.players().forEach(p->ids.add(p.getUUID()));
  layers.entrySet().removeIf(en->{if(!ids.contains(en.getKey())){en.getValue().setAnimation(null);return true;}return false;});
  forced.removeIf(id->!ids.contains(id));
  states.entrySet().removeIf(en->mc.level.getGameTime()-en.getValue().received()>=60);
 }
 @SubscribeEvent public static void overlay(RenderGuiEvent.Post e){
  var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null)return;
  if(mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof net.minecraft.world.entity.player.Player p&&p.distanceToSqr(mc.player)<=36&&p.isAlive()){
   var v=view(p.getUUID());if(v==null||!v.down())return;
   var text=Component.translatable("overlay.knockedback.rescuable",net.fretux.knockedback.client.ClientKeyHandler.HELP_UP_KEY.getTranslatedKeyMessage());int w=mc.font.width(text),x=(mc.getWindow().getGuiScaledWidth()-w)/2,y=mc.getWindow().getGuiScaledHeight()/2+18;
   e.getGuiGraphics().fill(x-5,y-3,x+w+5,y+12,0xB0000000);e.getGuiGraphics().drawString(mc.font,text,x,y,0xFFE4A3,true);
  }
 }
}
