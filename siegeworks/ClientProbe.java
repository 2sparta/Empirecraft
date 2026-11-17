package ua.empirecraft.siegeclienttest;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
@Mod("empirecraft_kitchen_client_probe")
public final class ClientProbe {
 boolean loaded,done;int ticks,frames,step;Catalog catalog;
 public ClientProbe(){MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::frame);}
 void frame(TickEvent.RenderTickEvent e){if(e.phase==TickEvent.Phase.END&&Minecraft.getInstance().screen instanceof Catalog)frames++;}
 static final class Catalog extends Screen {
  final List<LivingEntity> engines=new ArrayList<>();final List<ItemStack> items=new ArrayList<>();boolean showItems;int page,boardItems;
  Catalog(){super(Component.literal("Siegeworks integration verification"));var mc=Minecraft.getInstance();for(var e:ForgeRegistries.ENTITY_TYPES.getEntries())if(e.getKey().location().getNamespace().equals("siegeworks")){var entity=e.getValue().create(mc.level);if(entity instanceof LivingEntity living)engines.add(living);}for(var e:ForgeRegistries.ITEMS.getEntries())if(e.getKey().location().getNamespace().equals("siegeworks"))items.add(new ItemStack(e.getValue()));for(var e:ForgeRegistries.ITEMS.getEntries())if(e.getKey().location().getNamespace().equals("mctabletop")){items.add(new ItemStack(e.getValue()));boardItems++;}if(boardItems!=36)throw new AssertionError("Expected 36 Block & Board items, got "+boardItems);}
  @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xff20272a);g.drawCenteredString(font,showItems?"Siegeworks + Block & Board: item models":"Siegeworks: all siege engine models",width/2,6,0xddc18a);
   if(!showItems){int columns=4,cw=width/columns,ch=(height-32)/3;for(int i=0;i<engines.size();i++){var e=engines.get(i);int x=cw/2+(i%columns)*cw,y=23+(i/columns)*ch+ch-12;int scale=Math.max(2,(int)(42/Math.max(e.getBbHeight(),e.getBbWidth())));InventoryScreen.renderEntityInInventoryFollowsMouse(g,x,y,scale,0,0,e);g.drawCenteredString(font,ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).getPath(),x,y+3,0xbac5ca);}}
   else{int cols=10,rows=5,cw=width/cols,ch=(height-35)/rows;for(int i=0;i<50;i++){int index=page*50+i;if(index>=items.size())break;g.renderItem(items.get(index),cw/2-8+(i%cols)*cw,25+(i/cols)*ch);}}
   g.drawCenteredString(font,"Forge 1.20.1 / Axiomata / Recruits 1.15.2",width/2,height-12,0xa1adb0);
  }
 }
 void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),c->{});}
 void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||done)return;try{var mc=Minecraft.getInstance();if(!loaded&&mc.screen!=null&&mc.screen.getClass().getName().endsWith("EmpireHome")){loaded=true;mc.createWorldOpenFlows().loadLevel(mc.screen,"KITCHEN-TEST");return;}if(loaded&&mc.level==null&&(mc.screen instanceof ConfirmScreen||mc.screen instanceof BackupConfirmScreen)){for(var w:mc.screen.children())if(w instanceof net.minecraft.client.gui.components.Button b){b.onPress();break;}}
  if(mc.level==null||mc.player==null)return;if(++ticks<100)return;
  if(catalog==null){if(ForgeRegistries.ENTITY_TYPES.getKeys().stream().anyMatch(k->k.getNamespace().equals("siegemachines")))throw new AssertionError("Old Siege Machines still installed");catalog=new Catalog();if(catalog.engines.size()!=12)throw new AssertionError("Expected 12 engines, got "+catalog.engines.size());mc.setScreen(catalog);ticks=0;return;}
  if(step==0){capture("01-siegeworks-engines");catalog.showItems=true;step=1;ticks=0;return;}
  if(step==2){if(++ticks<140)return;capture("03-siegeworks-jei");mc.screen.onClose();if(!ua.empirecraft.siegeworks.client.SiegeJei.show(new ItemStack(ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("mctabletop:go_fish_board")))))throw new AssertionError("Go Fish JEI missing");step=3;ticks=0;return;}
  if(step==3){capture("05-blockandboard-jei");finish();return;}
  if(catalog.page<(catalog.items.size()-1)/50){capture("02-siegeworks-items-"+catalog.page);catalog.page++;ticks=0;return;}
  capture("03-siegeworks-items-"+catalog.page);if(frames<60)throw new AssertionError("Models were not rendered");var output=new ItemStack(ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("siegeworks:culverin_spawner")));if(!ua.empirecraft.siegeworks.client.SiegeJei.show(output)||mc.screen==catalog)throw new AssertionError("Construction recipe not shown in actual JEI");step=2;ticks=0;
 }catch(Throwable t){done=true;System.out.println("KITCHEN_CLIENT_FAIL SIEGE "+t);t.printStackTrace();}}
 void finish(){var mc=Minecraft.getInstance();done=true;System.out.println("KITCHEN_CLIENT_GAMEPLAY_PASS SIEGE engines="+catalog.engines.size()+" items="+catalog.items.size()+" boardItems="+catalog.boardItems+" frames="+frames+" jei=true");mc.tell(()->{mc.level.disconnect();mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));mc.setScreen(new TitleScreen());System.out.println("KITCHEN_CLIENT_PASS SIEGE engines="+catalog.engines.size()+" items="+catalog.items.size()+" boardItems="+catalog.boardItems+" frames="+frames+" jei=true");});}

}
