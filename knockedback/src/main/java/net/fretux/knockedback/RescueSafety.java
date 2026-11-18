package net.fretux.knockedback;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.event.TickEvent;
public final class RescueSafety {
 private record Anchor(Vec3 pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
 private static final Map<UUID,Anchor> anchors=new HashMap<>();
 public static void started(ServerPlayer helper){
  if(HelpUpHandler.isHelping(helper.getUUID()))anchors.putIfAbsent(helper.getUUID(),new Anchor(helper.position(),helper.level().dimension()));
 }
 public static void validate(){
  var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
  for(var id:new ArrayList<>(anchors.keySet())){
   var p=server.getPlayerList().getPlayer(id);var a=anchors.get(id);
   if(p==null||!HelpUpHandler.isHelping(id)){anchors.remove(id);continue;}
   if(p.level().dimension()!=a.dimension()||p.position().distanceToSqr(a.pos())>.1225){HelpUpHandler.cancelHelpUpByHelper(id);anchors.remove(id);}
  }
 }
 public static void tick(TickEvent.PlayerTickEvent event){if(event.phase==TickEvent.Phase.END&&event.side.isServer())validate();}
}
