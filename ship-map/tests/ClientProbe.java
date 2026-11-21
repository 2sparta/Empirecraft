package ua.empirecraft.kitchenclienttest;

import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.mod.common.*;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import org.valkyrienskies.core.api.ships.*;
import ua.empirecraft.townymap.*;
import ua.empirecraft.townymap.client.*;
import journeymap.client.ui.UIManager;
import journeymap.client.ui.fullscreen.Fullscreen;

/** Actual integrated client/server, real JourneyMap portraits, contextual keyboard injection and C2S validation. */
@Mod("empirecraft_kitchen_client_probe")public final class ClientProbe {
 int phase,ticks,total,checks,scopedFrames;boolean done,used;volatile boolean sceneReady;volatile Throwable failure;volatile long first=-1,second=-1;
 ShipObservation captured;
 public ClientProbe(){MinecraftForge.EVENT_BUS.addListener(this::tick);
  net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e)->e.enqueueWork(()->ValkyrienSkiesMod.getApi().registerAttachment(Motor.class)));}
 public static final class Motor implements ShipPhysicsListener {
  public void physTick(PhysShip b,org.valkyrienskies.core.api.world.PhysLevel level){if(b.isStatic())return;var v=b.getVelocity();b.applyInvariantForce(new org.joml.Vector3d((2-v.x())*b.getMass()*2,(10-v.y()*2)*b.getMass(),-v.z()*b.getMass()*2));}
 }
 void check(boolean ok,String s){checks++;if(!ok)throw new AssertionError(s);}
 void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),c->{});}
 void next(int p){phase=p;ticks=0;}
 void setup(Minecraft mc){var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{
  var server=mc.getSingleplayerServer();var level=server.overworld();Set<BlockPos> blocks=new HashSet<>();
  for(int z=64;z<86;z++){int radius=z<68?z-63:4;for(int x=68-radius;x<=68+radius;x++){var p=new BlockPos(x,80,z);level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),2);blocks.add(p);}}
  for(int y=81;y<=88;y++){var p=new BlockPos(68,y,72);level.setBlock(p,Blocks.OAK_FENCE.defaultBlockState(),2);blocks.add(p);}
  for(int x=67;x<=69;x++)for(int z=78;z<=81;z++){var p=new BlockPos(x,81,z);level.setBlock(p,Blocks.WHITE_WOOL.defaultBlockState(),2);blocks.add(p);}
  var a=ShipAssembler.assembleToShip(level,blocks,1);a.setStatic(true);first=a.getId();blocks=new HashSet<>();
  for(int x=102;x<119;x++)for(int z=64;z<86;z++)if(x<109||z>77){var p=new BlockPos(x,80,z);level.setBlock(p,Blocks.DARK_OAK_PLANKS.defaultBlockState(),2);blocks.add(p);}
  for(int x=103;x<108;x++)for(int z=68;z<75;z++){var p=new BlockPos(x,81,z);level.setBlock(p,Blocks.RED_WOOL.defaultBlockState(),2);blocks.add(p);}
  var b=ShipAssembler.assembleToShip(level,blocks,1);b.setStatic(true);second=b.getId();
  var player=server.getPlayerList().getPlayer(uuid);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();player.setNoGravity(true);
  player.getInventory().setItem(0,new ItemStack(Items.SPYGLASS));player.getInventory().selected=0;player.inventoryMenu.broadcastChanges();player.teleportTo(level,88,86,55,Set.of(),0,18);sceneReady=true;
 }catch(Throwable t){failure=t;}});}
 void openMap(){var map=new Fullscreen();Minecraft.getInstance().setScreen(map);map.setFollow(false);map.centerOn(90,75);}
 ShipObservation saved(){return ShipMapLayer.observations().stream().filter(s->s.ship()==first).findFirst().orElse(null);}
 void aim(Minecraft mc){var ship=VSGameUtilsKt.getShipObjectWorld(mc.level).getLoadedShips().getById(first);if(ship==null||ship.getShipAABB()==null)return;
  var box=ship.getShipAABB();var at=ShipSight.worldPoint(ship,new Vec3(box.minX()+4.5,box.minY()+3.5,box.minZ()+8.5));var d=at.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
 void press(Minecraft mc){mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),76,0,1,0);mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),76,0,0,0);}
 void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||done)return;try{var mc=Minecraft.getInstance();if(failure!=null)throw new AssertionError("Server scene",failure);if(++total>2600)throw new AssertionError("Timed out phase="+phase+" focus="+ShipReconClient.focus()+" live="+ShipMapLayer.view().size());
  if(phase==0){if(mc.screen==null||mc.getOverlay()!=null||++ticks<60)return;mc.createWorldOpenFlows().loadLevel(mc.screen,"KITCHEN-TEST");next(1);return;}
  if(mc.level==null&&(mc.screen instanceof ConfirmScreen||mc.screen instanceof BackupConfirmScreen))for(var w:mc.screen.children())if(w instanceof net.minecraft.client.gui.components.Button b){b.onPress();break;}
  if(mc.level==null||mc.player==null)return;
  if(mc.screen!=null&&mc.screen.getClass().getName().equals("journeymap.client.ui.dialog.about.AboutScreen")){
   boolean clicked=false;for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button){String label=button.getMessage().getString();if(label.equals(net.minecraft.network.chat.CommonComponents.GUI_DONE.getString())||label.equals(net.minecraft.network.chat.CommonComponents.GUI_BACK.getString())){System.out.println("SHIP_MAP_DISMISS_ABOUT "+label);button.onPress();clicked=true;break;}}
   if(!clicked)mc.screen.onClose();if(phase==3||phase==9)openMap();ticks=0;return;
  }
  ticks++;
  if(phase==1){if(ticks<100)return;mc.options.hideGui=false;mc.options.pauseOnLostFocus=false;mc.getToasts().clear();setup(mc);next(2);return;}
  if(phase==2){if(!sceneReady||ticks<120)return;
   var rows=ShipMapLayer.view();if(ticks%80==0){System.out.println("SHIP_MAP_DIAG pos="+mc.player.position()+" render="+mc.options.getEffectiveRenderDistance()+" expected="+first+","+second+" rows="+rows);var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayer(uuid);var m=StrategyNetwork.class.getDeclaredMethod("present",net.minecraft.server.level.ServerPlayer.class);m.setAccessible(true);System.out.println("SHIP_MAP_SERVER_DIAG present="+m.invoke(null,player)+" pos="+player.position()+" view="+server.getPlayerList().getViewDistance()+" effective="+ShipViewDistance.blocks(player));for(var ship:VSGameUtilsKt.getShipObjectWorld(player.serverLevel()).getLoadedShips())System.out.println("SHIP_MAP_HULL_DIAG id="+ship.getId()+" bounds="+ship.getWorldAABB()+" dimension="+ship.getChunkClaimDimension());}catch(Exception ex){ex.printStackTrace();}});}
   if(rows.stream().filter(r->r.ship()==first||r.ship()==second).count()<2||rows.stream().filter(r->r.ship()==first||r.ship()==second).anyMatch(r->ShipIconCache.image(r.ship(),r.icon())==null)){if(ticks<400)return;}
   check(saved()==null,"Merely seeing a hull must not save its position");var a=rows.stream().filter(r->r.ship()==first).findFirst().orElseThrow();var b=rows.stream().filter(r->r.ship()==second).findFirst().orElseThrow();
   check(!a.icon().isEmpty()&&!b.icon().isEmpty()&&!a.icon().equals(b.icon()),"Different actual hulls produce different portraits");check(ShipIconCache.image(first,a.icon())!=null&&ShipIconCache.image(second,b.icon())!=null,"Portraits arrive over authenticated network");openMap();next(3);return;}
  if(phase==3){if(ticks<70)return;check(mc.screen instanceof Fullscreen,"Real JourneyMap opened: "+(mc.screen==null?"null":mc.screen.getClass().getName()));check(ShipMapLayer.view().stream().filter(r->r.ship()==first||r.ship()==second).count()==2,"Live contacts remain visible on the full map, including a paused integrated game");capture("01-live-ship-portraits");mc.setScreen(null);mc.getSingleplayerServer().execute(()->{var ship=VSGameUtilsKt.getShipObjectWorld(mc.getSingleplayerServer().overworld()).getLoadedShips().getById(first);ship.setAttachment(Motor.class,new Motor());ship.setStatic(false);});next(4);return;}
  if(phase==4){aim(mc);mc.options.keyUse.setDown(true);if(!used&&mc.player.getMainHandItem().is(Items.SPYGLASS)){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);used=true;}
   if(ticks<70||ShipReconClient.focus()!=first){if(ticks<300)return;}
   check(ShipReconClient.focus()==first&&mc.player.isUsingItem(),"Scoped target and contextual hint focus="+ShipReconClient.focus()+" expected="+first+" using="+mc.player.isUsingItem());check(saved()==null,"Spyglass alone must not create a persistent marker");
   if(++scopedFrames<12)return;capture("02-spyglass-save-hint");press(mc);next(5);return;}
  if(phase==5){aim(mc);if(ticks<40)return;check(mc.screen==null,"L records a sighting instead of opening advancements");captured=saved();check(captured!=null,"L traverses actual C2S handler and saves an observation");
   check(captured.speed()>.1,"The saved speed is physical ship velocity");check(Math.abs(captured.heading()-captured.hullYaw())>20,"Course and hull rotation remain independent");mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);next(6);return;}
  if(phase==6){if(ticks<80)return;check(captured.equals(saved()),"Live movement cannot silently overwrite a saved sighting");check(!ShipReconClient.key(76,0,1,0),"L remains available outside the spyglass context");
   StrategyNetwork.CHANNEL.sendToServer(new StrategyNetwork.Save(first));var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);p.teleportTo(p.serverLevel(),900,90,900,Set.of(),0,0);});next(7);return;}
  if(phase==7){if(ticks<100||mc.player.getX()<800)return;check(captured.equals(saved()),"Forged unscoped save does not replace history");check(ShipMapLayer.view().stream().noneMatch(r->r.ship()==second),"An unsaved hull disappears outside render distance");
   check(ShipMapLayer.view().stream().filter(r->r.ship()==first).findFirst().orElseThrow().equals(captured),"Saved contact freezes position, velocity and course outside view distance");
   ShipIconCache.clear();ShipMapLayer.refresh();next(8);return;}
  if(phase==8){if(ticks<80)return;if(ShipIconCache.image(first,captured.icon())==null){if(ticks<240)return;}check(ShipIconCache.image(first,captured.icon())!=null,"Historical portrait is recovered by an authorized bounded request after cache eviction");openMap();((Fullscreen)mc.screen).centerOn(captured.x(),captured.z());next(9);return;}
  if(phase==9){if(ticks<80)return;check(captured.equals(saved()),"History remains unchanged after image recovery");capture("03-saved-ship-outside-range");done=true;System.out.println("KITCHEN_CLIENT_PASS shipMap=true checks="+checks+" portraits=2 contextualL=true frozenHistory=true backfill=true");}
 }catch(Throwable t){done=true;capture("07-ship-map-failure-"+phase);System.out.println("KITCHEN_CLIENT_FAIL SHIP_MAP phase="+phase+" "+t);t.printStackTrace();}}
}
