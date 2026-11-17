package ua.empirecraft.armoryclienttest;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import ua.empirecraft.armory.*;
import ua.empirecraft.armory.client.WorkshopScreen;

/** Test-only integrated world: real screens, menu packets, recipe sync and portable work. */
@Mod("empirecraft_kitchen_client_probe")
public final class ClientProbe {
    final BlockPos pos=new BlockPos(0,100,0);
    int uiStep,uiWait;boolean uiDone;
    boolean loaded,done,queued,exiting,transferred,selectedByPacket,alternateRequested,catalogOpened;int ticks,checks,stage=-1,wait,frames,catalogFrames;volatile boolean ready;volatile Throwable error;
    public ClientProbe(){MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::frame);}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),c->{});}
    void fail(Throwable t){if(done)return;done=true;System.out.println("KITCHEN_CLIENT_FAIL ARMORY "+t);t.printStackTrace();}
    void frame(TickEvent.RenderTickEvent event){if(event.phase==TickEvent.Phase.END&&!done){if(Minecraft.getInstance().screen instanceof WorkshopScreen)frames++;if(Minecraft.getInstance().screen instanceof OttomanCatalog)catalogFrames++;}}
    static boolean target(AssemblyRecipe recipe){return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(recipe.result.getItem()).toString().equals("minecraft:iron_chestplate");}
    static final class OttomanCatalog extends net.minecraft.client.gui.screens.Screen {
        final java.util.List<ItemStack> models=java.util.List.of(new ItemStack(Armory.TABLE_ITEM.get()));
        OttomanCatalog(){super(net.minecraft.network.chat.Component.literal("Armorer workbench model"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xff20272a);g.drawCenteredString(font,"Armorer workshop · full block model",width/2,12,0xddc18a);g.pose().pushPose();g.pose().translate(width/2-64,height/2-64,0);g.pose().scale(8,8,1);g.renderItem(models.get(0),0,0);g.pose().popPose();g.drawCenteredString(font,"Original opaque wood, iron straps and worktop",width/2,height-20,0xa1adb0);}
    }
    void click(int x,int y){var mc=Minecraft.getInstance();check(mc.screen instanceof WorkshopScreen,"Workbench remains open");mc.screen.mouseClicked((mc.screen.width-320)/2.0+x,(mc.screen.height-238)/2.0+y,0);}
    void assemble(){var mc=Minecraft.getInstance();var screen=(WorkshopScreen)mc.screen;check(screen.visiblePlan()!=null&&screen.visiblePlan().complete,"Visible requirements agree with prepared server supplies");click(205,124);}
    boolean interfaceTest(){var mc=Minecraft.getInstance();if(++uiWait<16)return false;uiWait=0;
        if(uiStep==0){var screen=(WorkshopScreen)mc.screen;check(screen.width>=320&&screen.height>=238,"GUI fits minimum Minecraft viewport");check(screen.visiblePlan().complete,"Ready foundation count shown");var manuals=GunsmithManual.all();check(manuals.size()==7,"All six gunsmith products plus researched lock shown in JEI");var lock=manuals.stream().filter(r->r.selection()==3&&!r.researched()).findFirst().orElseThrow();check(lock.operations()==24,"Standard flintlock manual");check(manuals.stream().anyMatch(r->r.selection()==3&&r.researched()&&r.operations()==6),"Blueprint flintlock manual");click(187,69);check(!screen.visiblePlan().complete,"Future stage shows missing components");uiStep++;return false;}
        if(uiStep==1){capture("01-workbench-requirements");var screen=(WorkshopScreen)mc.screen;check(!screen.visiblePlan().needs.isEmpty(),"Counted requirement rows shown");click(123,85);check(mc.screen!=screen,"Clicking a component opens actual JEI recipes");uiStep++;return false;}
        if(uiStep==2){check(!(mc.screen instanceof WorkshopScreen),"JEI is open");capture("01-component-recipe-jei");mc.screen.onClose();uiStep++;return false;}
        if(uiStep==3){check(mc.screen instanceof WorkshopScreen,"JEI returns to same table");click(160,31);server(()->{var table=(WorkshopEntity)mc.getSingleplayerServer().overworld().getBlockEntity(pos);var part=new ItemStack(Armory.part("steel","front"));net.dries007.tfc.common.capabilities.heat.HeatCapability.setTemperature(part,875);table.inventory.setStackInSlot(1,part);});uiStep++;return false;}
        if(uiStep==4){capture("01-workbench-treatment");click(260,31);uiStep++;return false;}
        if(uiStep==5){capture("01-workbench-repair");click(303,12);uiStep++;return false;}
        if(uiStep==6){capture("01-workbench-help");check(mc.screen.keyPressed(256,0,0),"Esc dismisses help without closing table");click(50,31);click(138,69);check(ua.empirecraft.armory.client.ArmoryJei.showRecipe(new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("empirecraft_forging:flintlock")))),"Flintlock has an actual JEI recipe display");check(!(mc.screen instanceof WorkshopScreen),"Flintlock recipe UI opened");uiStep++;return false;}
        if(uiStep==7){check(mc.font.split(net.minecraft.network.chat.Component.translatable("armory.gunsmith.instruction"),232).size()<=5,"JEI instructions fit the minimum viewport");var model=mc.getItemRenderer().getModel(new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("empirecraft_forging:lockwork"))),mc.level,mc.player,0);check(!model.getParticleIcon().contents().name().getPath().contains("missingno"),"Precision lockwork has a real texture");check(!(mc.screen instanceof WorkshopScreen),"Flintlock JEI remains open");capture("01-flintlock-jei");mc.screen.onClose();uiDone=true;return true;}
        return false;
    }
    void server(Runnable r){ready=false;Minecraft.getInstance().getSingleplayerServer().execute(()->{try{r.run();}catch(Throwable t){error=t;}finally{ready=true;}});}
    static void supply(WorkshopEntity table,AssemblyRecipe r,int stage){
        for(int i=3;i<15;i++)table.inventory.setStackInSlot(i,ItemStack.EMPTY);
        for(var in:r.stage(stage)){var s=in.getItems()[0].copy();if(s.getItem() instanceof PartItem)s=Parts.sample(s.getItem());for(int i=3;i<15;i++){var old=table.inventory.getStackInSlot(i);if(old.isEmpty()){table.inventory.setStackInSlot(i,s);break;}if(ItemStack.isSameItemSameTags(old,s)){old.grow(1);break;}}}
    }
    void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||done||exiting)return;
        try{
            var mc=Minecraft.getInstance();
            if(!loaded&&mc.screen!=null&&mc.screen.getClass().getName().endsWith("EmpireHome")){loaded=true;mc.createWorldOpenFlows().loadLevel(mc.screen,"KITCHEN-TEST");return;}
            if(loaded&&mc.level==null&&(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen||mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen)){for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b){b.onPress();break;}}
            if(mc.level==null||mc.player==null)return;
            if(error!=null)throw new RuntimeException(error);
            if(catalogOpened){if(++ticks<100)return;check(catalogFrames>40,"Full workbench block model rendered");capture("03-workbench-block-model");finish();return;}
            if(!queued){queued=true;server(()->{
                var srv=mc.getSingleplayerServer();var level=srv.overworld();level.getChunkAt(pos);for(int x=-5;x<=7;x++)for(int z=-5;z<=5;z++)level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE_BRICKS.defaultBlockState());level.setBlockAndUpdate(pos,Armory.TABLE.get().defaultBlockState());
                var table=(WorkshopEntity)level.getBlockEntity(pos);var all=AssemblyRecipe.all(level);var r=all.stream().filter(ClientProbe::target).findFirst().orElseThrow();table.select(all.indexOf(r));supply(table,r,0);
                var player=srv.getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(GameType.CREATIVE);player.connection.teleport(.5,101,3.5,180,30);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);NetworkHooks.openScreen(player,table,pos);
            });return;}
            if(selectedByPacket&&!uiDone&&(uiStep==2||uiStep==7)){interfaceTest();return;}
            if(!ready||!(mc.player.containerMenu instanceof WorkshopMenu menu)||!(mc.screen instanceof WorkshopScreen))return;
            ticks++;
            if(ticks<80)return;
            check(!AssemblyRecipe.all(mc.level).isEmpty(),"Custom recipes synchronized to client");check(menu.selected()>=0,"Server-selected workpiece synchronized");
            if(!selectedByPacket){var list=AssemblyRecipe.all(mc.level);int last=list.size()-1;if(!alternateRequested){check(last>255,"Selection test exceeds the vanilla byte limit");ArmoryNetwork.select(menu.containerId,last);alternateRequested=true;wait=0;return;}if(menu.selected()!=last){if(++wait>180)throw new AssertionError("Large-index selection was not synchronized");return;}var target=list.stream().filter(ClientProbe::target).findFirst().orElseThrow();ArmoryNetwork.select(menu.containerId,list.indexOf(target));selectedByPacket=true;ticks=0;return;}
            if(!uiDone){interfaceTest();return;}
            if(stage==-1){capture("01-armorer-workbench");stage=0;assemble();wait=0;return;}
            if(stage<4){
                if(++wait>180)throw new AssertionError("Network assembly stalled stage="+stage+" client="+menu.stage()+" status="+menu.status());
                if(stage<3&&menu.stage()==stage+1){stage++;wait=0;
                    if(stage==1&&!transferred){transferred=true;capture("02-portable-workpiece");server(()->{
                        var srv=mc.getSingleplayerServer();var level=srv.overworld();var old=(WorkshopEntity)level.getBlockEntity(pos);var work=ItemStack.of(old.inventory.getStackInSlot(0).save(new CompoundTag()));old.inventory.setStackInSlot(0,ItemStack.EMPTY);var nextPos=pos.east(3);level.setBlockAndUpdate(nextPos,Armory.TABLE.get().defaultBlockState());var next=(WorkshopEntity)level.getBlockEntity(nextPos);next.inventory.setStackInSlot(0,work);var r=next.recipe();supply(next,r,1);var player=srv.getPlayerList().getPlayer(mc.player.getUUID());player.closeContainer();NetworkHooks.openScreen(player,next,nextPos);
                    });wait=-80;return;}
                    server(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var table=(WorkshopEntity)p.serverLevel().getBlockEntity(transferred?pos.east(3):pos);supply(table,table.recipe(),stage);});wait=-10;return;
                }
                if(wait==-1){assemble();return;}
                if(stage==3&&menu.slots.get(2).hasItem()){check(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(menu.slots.get(2).getItem().getItem()).toString().equals("minecraft:iron_chestplate"),"Real armor output reaches client");check(menu.slots.get(2).getItem().getTag().contains(Parts.DATA),"Installed component data synchronized");stage=4;capture("03-assembled-armor");ticks=0;}
                return;
            }
            if(ticks<100)return;
            check(frames>50,"Workbench was actually rendered");mc.player.closeContainer();var catalog=new OttomanCatalog();check(catalog.models.size()==1,"Full workbench cube has a model fixture");mc.setScreen(catalog);catalogOpened=true;ticks=0;
        }catch(Throwable t){fail(t);}
    }
    void finish(){var mc=Minecraft.getInstance();exiting=true;System.out.println("KITCHEN_CLIENT_GAMEPLAY_PASS ARMORY checks="+checks+" frames="+frames+" stages=4 portable=true workbench_ui=true jei_component_navigation=true full_cube=true");mc.tell(()->{try{mc.level.disconnect();mc.clearLevel(new net.minecraft.client.gui.screens.GenericDirtMessageScreen(net.minecraft.network.chat.Component.translatable("menu.savingLevel")));mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());check(mc.level==null,"Clean world exit");done=true;System.out.println("KITCHEN_CLIENT_PASS ARMORY checks="+checks+" frames="+frames+" stages=4 portable=true workbench_ui=true jei_component_navigation=true full_cube=true");}catch(Throwable t){fail(t);}});}
}
