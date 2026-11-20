package ua.empirecraft.kitchenclienttest;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.nbt.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.launcherclient.*;
@Mod("empirecraft_kitchen_client_probe") public class ClientProbe {
 int ticks,phase;boolean loaded,done;volatile Throwable serverError;volatile boolean ready;
 Item epicHead,epicChest;
 public ClientProbe(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick);}
 void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),c->{});}
 void server(Runnable task){ready=false;Minecraft.getInstance().getSingleplayerServer().execute(()->{try{task.run();}catch(Throwable t){serverError=t;}finally{ready=true;}});}
 Item epic(EquipmentSlot slot){return ForgeRegistries.ITEMS.getEntries().stream().filter(e->e.getKey().location().getNamespace().equals("magistuarmory")&&e.getValue() instanceof ArmorItem a&&a.getEquipmentSlot()==slot).sorted(java.util.Comparator.comparing(e->e.getKey().location().toString())).map(Map.Entry::getValue).findFirst().orElseThrow();}
 void press(String key){var mc=Minecraft.getInstance();String label=net.minecraft.network.chat.Component.translatable("empirecraft.home."+key).getString();for(var c:mc.screen.children())if(c instanceof Button b&&b.getMessage().getString().equals(label)){b.onPress();return;}throw new AssertionError("Missing button "+key);}
 void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||done)return;try{
  var mc=Minecraft.getInstance();if(serverError!=null)throw new AssertionError(serverError);
  if(phase==0){if(!(mc.screen instanceof EmpireHome)||mc.getOverlay()!=null||++ticks<40)return;
   check(mc.level==null,"First menu has no world");check(HomePreferences.closeUp(),"Waist up by default");check(!HomeEquipment.known(),"Fresh account has no invented equipment");capture("01-home-empty");loaded=true;mc.createWorldOpenFlows().loadLevel(mc.screen,"KITCHEN-TEST");phase=1;ticks=0;return;}
  if(loaded&&mc.level==null&&(mc.screen instanceof ConfirmScreen||mc.screen instanceof BackupConfirmScreen)){for(var w:mc.screen.children())if(w instanceof Button b){b.onPress();break;}}
  if(phase<=4&&(mc.level==null||mc.player==null))return;
  if(++ticks<80)return;
  if(phase==1){server(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.setHealth(20);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);var h=new ItemStack(Items.IRON_HELMET);var inner=new ListTag();inner.add(new ItemStack(Items.LEATHER_HELMET).save(new CompoundTag()));h.getOrCreateTag().put("EmpirecraftArmorLayers",inner);p.setItemSlot(EquipmentSlot.HEAD,h);p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));p.inventoryMenu.broadcastChanges();});phase=2;ticks=0;return;}
  if(!ready&&phase<=4)return;
  if(phase==2){check(mc.player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),"Server equipment reaches client");HomeEquipment.capture(mc.player,LauncherClient.HOST,LauncherClient.PORT);HomeEquipment.flush();HomeEquipment.reload();check(HomeEquipment.get(EquipmentSlot.HEAD).is(Items.IRON_HELMET),"Armor survives disk reload");check(!HomeEquipment.get(EquipmentSlot.HEAD).getOrCreateTag().contains("EmpirecraftArmorLayers"),"Only outermost piece cached");check(mc.player.getItemBySlot(EquipmentSlot.HEAD).getOrCreateTag().contains("EmpirecraftArmorLayers"),"Actual worn stack was not edited");epicHead=epic(EquipmentSlot.HEAD);epicChest=epic(EquipmentSlot.CHEST);server(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(epicHead));p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(epicChest));p.inventoryMenu.broadcastChanges();});phase=3;ticks=0;return;}
  if(phase==3){check(mc.player.getItemBySlot(EquipmentSlot.HEAD).getItem()==epicHead,"Modded armor synced");HomeEquipment.capture(mc.player,"example.invalid",LauncherClient.PORT);check(HomeEquipment.get(EquipmentSlot.HEAD).is(Items.IRON_HELMET),"Other server cannot overwrite cache");HomeEquipment.capture(mc.player,LauncherClient.HOST,LauncherClient.PORT);HomeEquipment.flush();HomeEquipment.reload();check(HomeEquipment.get(EquipmentSlot.HEAD).getItem()==epicHead,"Current modded equipment cached");mc.level.disconnect();mc.clearLevel(new ProgressScreen(false));mc.setScreen(new EmpireHome());phase=5;ticks=0;return;}
  if(!(mc.screen instanceof EmpireHome))return;
  check(mc.level==null,"Menu armor render creates no world");check(!HomePortrait.active(),"Scoped renderer did not leak");
  if(phase==5){check(HomeArmor.renderedSlots.containsAll(List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST)),"Modded head and chest rendered offline");capture("02-home-waist-armor");press("waist");phase=6;ticks=0;return;}
  if(phase==6){check(!HomePreferences.closeUp(),"Full body toggle persisted");capture("03-home-full-armor");press("armor");phase=7;ticks=0;return;}
  if(phase==7){check(!HomePreferences.armor(),"Armor hidden");capture("04-home-skin-only");press("full");press("skin_only");mc.getWindow().setWindowed(640,480);phase=8;ticks=0;return;}
  if(phase==8){check(HomePreferences.closeUp()&&HomePreferences.armor(),"Independent toggles restore");capture("05-home-small");mc.getWindow().setWindowed(1280,720);phase=9;ticks=0;return;}
  if(phase==9){capture("06-home-wide");mc.getWindow().setWindowed(1440,600);phase=10;ticks=0;return;}
  capture("07-home-short");done=true;System.out.println("KITCHEN_CLIENT_PASS menuEquipment=true diskCache=true outerOnly=true hostBound=true worldless=true modArmor=true toggles=true resize=true");
 }catch(Throwable t){done=true;System.out.println("KITCHEN_CLIENT_FAIL "+t);t.printStackTrace();}}
}
