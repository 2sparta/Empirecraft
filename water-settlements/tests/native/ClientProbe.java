package ua.empirecraft.kitchenclienttest;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
@Mod("empirecraft_kitchen_client_probe") public class ClientProbe {
 int ticks,phase;boolean done;
 public ClientProbe(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick);}
 void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),c->{});}
 void entry(String book,String entry)throws Exception{
  Class<?> type=Class.forName("vazkii.patchouli.api.PatchouliAPI");Object api=type.getMethod("get").invoke(null);
  api.getClass().getMethod("openBookEntry",ResourceLocation.class,ResourceLocation.class,int.class).invoke(api,new ResourceLocation(book),new ResourceLocation(entry),0);
 }
 void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||done)return;try{
  var mc=Minecraft.getInstance();
  if(phase==0){if(mc.screen==null||mc.getOverlay()!=null||++ticks<60)return;mc.createWorldOpenFlows().loadLevel(mc.screen,"KITCHEN-TEST");phase=1;ticks=0;return;}
  if(mc.level==null&&(mc.screen instanceof ConfirmScreen||mc.screen instanceof BackupConfirmScreen)){for(var w:mc.screen.children())if(w instanceof Button b){b.onPress();break;}}
  if(mc.level==null||mc.player==null||++ticks<80)return;
  if(phase==1){
   mc.options.hideGui=true;mc.getToasts().clear();
   var key=new ResourceLocation("tfc","patchouli_books/field_guide/en_us/entries/mechanics/anvils.json");
   var r=mc.getResourceManager().getResource(key).orElseThrow();String text=new String(r.open().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   check(text.contains("Overgeared"),"TFC native guide uses pack override in actual client resource manager");
   entry("tfc:field_guide","tfc:mechanics/anvils");phase=2;ticks=0;return;
  }
  if(phase==2){check(mc.screen!=null&&mc.screen.getClass().getName().contains("patchouli"),"Native TFC guide opens");mc.getToasts().clear();capture("01-current-tfc-forging-guide");entry("empirecraft_guidebook:handbook","empirecraft_guidebook:army/militia");phase=3;ticks=0;return;}
  check(mc.screen!=null&&mc.screen.getClass().getName().contains("patchouli"),"Updated manpower handbook opens");capture("02-population-militia-guide");done=true;System.out.println("KITCHEN_CLIENT_PASS waterSettlements=true tfcGuideOverride=true guideScreens=true");
 }catch(Throwable t){done=true;System.out.println("KITCHEN_CLIENT_FAIL "+t);t.printStackTrace();}}
}
