package ua.empirecraft.kitchenclienttest;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import me.mss1r.siegeworks.entity.base.AbstractSiegeEntity;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
@Mod("empirecraft_kitchen_client_probe")public final class ClientProbe {
 int phase,ticks,frames;boolean done;volatile boolean sceneReady;volatile Throwable failure;Gallery gallery;
 public ClientProbe(){MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener((TickEvent.RenderTickEvent e)->{if(e.phase==TickEvent.Phase.END&&Minecraft.getInstance().screen instanceof Gallery)frames++;});}
 void check(boolean ok,String s){if(!ok)throw new AssertionError(s);}
 void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),c->{});}
 void scene(Minecraft mc){var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{
  var server=mc.getSingleplayerServer();var level=server.overworld();Set<BlockPos> blocks=new HashSet<>();
  for(int x=64;x<80;x++)for(int z=64;z<80;z++){var p=new BlockPos(x,80,z);level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),2);blocks.add(p);}
  for(int x=64;x<80;x++)for(int z:new int[]{64,79}){var p=new BlockPos(x,81,z);level.setBlock(p,Blocks.OAK_FENCE.defaultBlockState(),2);blocks.add(p);}
  var ship=ShipAssembler.assembleToShip(level,blocks,1);ship.setStatic(true);
  String[] kinds={"culverin","serpentine","mons_meg"};for(int i=0;i<3;i++){var engine=(AbstractSiegeEntity)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("siegeworks:"+kinds[i])).create(level);engine.setPos(67+i*4,81.001,70);engine.setYRot(-90);engine.setTrackedYaw(-90);check(level.addFreshEntity(engine),"Live deck engine spawned");}
  var player=server.getPlayerList().getPlayer(uuid);player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);player.teleportTo(level,58,85,54,Set.of(),-32,12);sceneReady=true;
 }catch(Throwable t){failure=t;}});}
 void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||done)return;try{var mc=Minecraft.getInstance();if(failure!=null)throw new AssertionError("Server scene",failure);
  if(phase==0){if(mc.screen==null||mc.getOverlay()!=null||++ticks<60)return;mc.createWorldOpenFlows().loadLevel(mc.screen,"KITCHEN-TEST");phase=1;ticks=0;return;}
  if(mc.level==null&&(mc.screen instanceof ConfirmScreen||mc.screen instanceof BackupConfirmScreen))for(var w:mc.screen.children())if(w instanceof net.minecraft.client.gui.components.Button b){b.onPress();break;}
  if(mc.level==null||mc.player==null||++ticks<100)return;
  if(phase==1){mc.options.hideGui=true;mc.getToasts().clear();scene(mc);phase=2;ticks=0;return;}
  if(phase==2){if(!sceneReady)return;long live=0;for(var ent:mc.level.entitiesForRendering())if(ent instanceof AbstractSiegeEntity&&ent.getX()>62&&ent.getX()<82&&ent.getZ()>62&&ent.getZ()<82)live++;check(live==3,"Three synced live deck engines, actual="+live);capture("01-siegeworks-live-vs-deck");gallery=new Gallery();check(gallery.engines.size()==12,"All twelve engine models");mc.setScreen(gallery);phase=3;ticks=0;return;}
  check(frames>=30,"Real engine render frames");capture("02-siegeworks-compatible-models");done=true;System.out.println("KITCHEN_CLIENT_PASS siegeVs=true models=12 liveDeckEngines=3 frames="+frames);
 }catch(Throwable t){done=true;System.out.println("KITCHEN_CLIENT_FAIL SIEGE_VS "+t);t.printStackTrace();}}
 static final class Gallery extends Screen {
  final List<LivingEntity> engines=new ArrayList<>();Gallery(){super(Component.literal("Siegeworks · Valkyrien Skies"));var mc=Minecraft.getInstance();for(var e:ForgeRegistries.ENTITY_TYPES.getEntries())if(e.getKey().location().getNamespace().equals("siegeworks")){var entity=e.getValue().create(mc.level);if(entity instanceof LivingEntity living)engines.add(living);}}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xff20272a);g.drawCenteredString(font,title,width/2,8,0xddc18a);int cw=width/4,ch=(height-36)/3;for(int i=0;i<engines.size();i++){var ent=engines.get(i);int x=cw/2+i%4*cw,y=27+i/4*ch+ch-14;int scale=Math.max(2,(int)(42/Math.max(ent.getBbHeight(),ent.getBbWidth())));InventoryScreen.renderEntityInInventoryFollowsMouse(g,x,y,scale,0,0,ent);g.drawCenteredString(font,ForgeRegistries.ENTITY_TYPES.getKey(ent.getType()).getPath(),x,y+3,0xbac5ca);}}
 }
}
