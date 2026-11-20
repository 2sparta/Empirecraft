package ua.empirecraft.kitchenclienttest;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.trmt.*;
import com.mojang.blaze3d.platform.Lighting;
@Mod("empirecraft_kitchen_client_probe") public class ClientProbe {
 int ticks,phase;boolean done;
 final String[] icons={"create:peculiar_bell","recruits:recruit_spawn_egg","empirecraft_tfc:grass/chernozem","magistuarmory:silver_ingot","magistuarmory:chainmail_helmet","patchouli:guide_book","supplementaries:flag_white","magistuarmory:crown_decoration","tfc:thatch_bed","create:clipboard","tfc:food/wheat_bread","sereneseasons:calendar","tfc:metal/javelin/wrought_iron","create:cogwheel","supplementaries:key","supplementaries:globe"};
 public ClientProbe(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick);}
 void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),c->{});}
 void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||done)return;try{
  var mc=Minecraft.getInstance();
  if(phase==0){if(mc.screen==null||mc.getOverlay()!=null||++ticks<60)return;mc.createWorldOpenFlows().loadLevel(mc.screen,"KITCHEN-TEST");phase=1;ticks=0;return;}
  if(mc.level==null&&(mc.screen instanceof ConfirmScreen||mc.screen instanceof BackupConfirmScreen)){for(var w:mc.screen.children())if(w instanceof Button b){b.onPress();break;}}
  if(mc.level==null||mc.player==null||++ticks<80)return;
  if(phase==1){
   check(RegionalTrails.PATHS.size()==38,"All client soil registry entries");int models=0;
   for(var path:RegionalTrails.PATHS.values())for(int wear=0;wear<5;wear++)for(var direction:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
    var state=path.defaultBlockState().setValue(RememberedPath.WEAR,wear).setValue(RememberedPath.FACING,direction);
    var model=mc.getBlockRenderer().getBlockModel(state);
    check(!model.getParticleIcon().contents().name().getPath().contains("missing"),"Real soil particle "+state);
    for(var quad:model.getQuads(state,null,net.minecraft.util.RandomSource.create(1)))check(!quad.getSprite().contents().name().getPath().contains("missing"),"Real road texture "+state);
    models++;
   }
   check(models==760,"All wear/facing model variants");
   var container=new SimpleContainer(54);
   int[] slots={4,10,11,12,13,14,15,16,28,30,32,34,45,49,53,47};
   for(int i=0;i<icons.length;i++){var key=new ResourceLocation(icons[i]);check(ForgeRegistries.ITEMS.containsKey(key),"Icon registered "+key);container.setItem(slots[i],new ItemStack(ForgeRegistries.ITEMS.getValue(key)));}
   var menu=ChestMenu.sixRows(3,mc.player.getInventory(),container);mc.setScreen(new ContainerScreen(menu,mc.player.getInventory(),Component.literal("Місто · Алькульсан-сулейман")));phase=2;ticks=0;return;
  }
  if(phase==2){check(mc.screen instanceof ContainerScreen,"Standard chest screen");capture("01-town-vanilla-mod-icons");mc.getWindow().setWindowed(1440,900);mc.setScreen(new Gallery());phase=3;ticks=0;return;}
  capture("02-remembered-soil-paths");done=true;System.out.println("KITCHEN_CLIENT_PASS soilMemory=true models=760 vanillaChest=true modIcons=true");
 }catch(Throwable t){done=true;System.out.println("KITCHEN_CLIENT_FAIL "+t);t.printStackTrace();}}
 static final class Gallery extends Screen {
  final String[] soils={"empirecraft_tfc:grass/chernozem","tfc:grass/loam","tfc:clay/loam","tfc:red_kaolin_clay","tfc:sand/red","tfc:sand/yellow","tfc:sand/white","tfc:sand/black","tfc:sand/pink","tfc:sand/green","tfc:peat","tfc:clay_grass/silt"};
  Gallery(){super(Component.literal("Стежки, що пам’ятають ґрунт"));}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics g,int mx,int my,float partial){
   g.fill(0,0,width,height,0xff20251e);g.drawCenteredString(font,title,width/2,10,0xf2db9b);
   var mc=Minecraft.getInstance();int left=Math.max(8,(width-620)/2);
   g.drawString(font,"Ґрунт → поступове протоптування → повернення до того ж ґрунту",left,28,0xddd5bf);
   for(int row=0;row<soils.length;row++){
    var block=ForgeRegistries.BLOCKS.getValue(new ResourceLocation(soils[row]));var path=RegionalTrails.PATHS.get(block);int y=58+row*28;
    g.drawString(font,soils[row],left,y,0xddd5bf);
    for(int i=0;i<7;i++){
     BlockState state=i==0||i==6?block.defaultBlockState():path.defaultBlockState().setValue(RememberedPath.WEAR,i-1);
     var pose=g.pose();pose.pushPose();pose.translate(left+284+i*43,y+12,80);pose.scale(17,-17,17);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(30));pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(45));pose.translate(-.5,-.5,-.5);
     Lighting.setupFor3DItems();mc.getBlockRenderer().renderSingleBlock(state,pose,g.bufferSource(),0xf000f0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);g.flush();pose.popPose();
    }
   }
  }
 }
}
