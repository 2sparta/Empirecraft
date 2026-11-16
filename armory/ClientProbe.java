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
    boolean loaded,done,queued,exiting,transferred,selectedByPacket,alternateRequested;int ticks,checks,stage=-1,wait,frames;volatile boolean ready;volatile Throwable error;
    public ClientProbe(){MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::frame);}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),c->{});}
    void fail(Throwable t){if(done)return;done=true;System.out.println("KITCHEN_CLIENT_FAIL ARMORY "+t);t.printStackTrace();}
    void frame(TickEvent.RenderTickEvent event){if(event.phase==TickEvent.Phase.END&&!done&&Minecraft.getInstance().screen instanceof WorkshopScreen){frames++;}}
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
            if(!queued){queued=true;server(()->{
                var srv=mc.getSingleplayerServer();var level=srv.overworld();level.getChunkAt(pos);for(int x=-5;x<=7;x++)for(int z=-5;z<=5;z++)level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE_BRICKS.defaultBlockState());level.setBlockAndUpdate(pos,Armory.TABLE.get().defaultBlockState());
                var table=(WorkshopEntity)level.getBlockEntity(pos);var all=AssemblyRecipe.all(level);var r=all.stream().filter(a->a.result.is(Items.IRON_CHESTPLATE)&&a.base.isEmpty()).findFirst().orElseThrow();table.select(all.indexOf(r));supply(table,r,0);
                var player=srv.getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(GameType.CREATIVE);player.connection.teleport(.5,101,3.5,180,15);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);NetworkHooks.openScreen(player,table,pos);
            });return;}
            if(!ready||!(mc.player.containerMenu instanceof WorkshopMenu menu)||!(mc.screen instanceof WorkshopScreen))return;
            ticks++;
            if(ticks<80)return;
            check(!AssemblyRecipe.all(mc.level).isEmpty(),"Custom recipes synchronized to client");check(menu.selected()>=0,"Server-selected workpiece synchronized");
            if(!selectedByPacket){var list=AssemblyRecipe.all(mc.level);int last=list.size()-1;if(!alternateRequested){check(last>255,"Selection test exceeds the vanilla byte limit");ArmoryNetwork.select(menu.containerId,last);alternateRequested=true;wait=0;return;}if(menu.selected()!=last){if(++wait>180)throw new AssertionError("Large-index selection was not synchronized");return;}var target=list.stream().filter(r->r.result.is(Items.IRON_CHESTPLATE)&&r.base.isEmpty()).findFirst().orElseThrow();ArmoryNetwork.select(menu.containerId,list.indexOf(target));selectedByPacket=true;ticks=0;return;}
            if(stage==-1){capture("01-armorer-workbench");stage=0;mc.gameMode.handleInventoryButtonClick(menu.containerId,0);wait=0;return;}
            if(stage<4){
                if(++wait>180)throw new AssertionError("Network assembly stalled stage="+stage+" client="+menu.stage()+" status="+menu.status());
                if(stage<3&&menu.stage()==stage+1){stage++;wait=0;
                    if(stage==1&&!transferred){transferred=true;capture("02-portable-workpiece");server(()->{
                        var srv=mc.getSingleplayerServer();var level=srv.overworld();var old=(WorkshopEntity)level.getBlockEntity(pos);var work=ItemStack.of(old.inventory.getStackInSlot(0).save(new CompoundTag()));old.inventory.setStackInSlot(0,ItemStack.EMPTY);var nextPos=pos.east(3);level.setBlockAndUpdate(nextPos,Armory.TABLE.get().defaultBlockState());var next=(WorkshopEntity)level.getBlockEntity(nextPos);next.inventory.setStackInSlot(0,work);var r=next.recipe();supply(next,r,1);var player=srv.getPlayerList().getPlayer(mc.player.getUUID());player.closeContainer();NetworkHooks.openScreen(player,next,nextPos);
                    });wait=-80;return;}
                    server(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var table=(WorkshopEntity)p.serverLevel().getBlockEntity(transferred?pos.east(3):pos);supply(table,table.recipe(),stage);});wait=-10;return;
                }
                if(wait==-1){mc.gameMode.handleInventoryButtonClick(menu.containerId,0);return;}
                if(stage==3&&menu.slots.get(2).hasItem()){check(menu.slots.get(2).getItem().is(Items.IRON_CHESTPLATE),"Real output reaches client");check(menu.slots.get(2).getItem().getTag().contains(Parts.DATA),"Installed component data synchronized");stage=4;capture("03-assembled-armor");ticks=0;}
                return;
            }
            if(ticks<100)return;
            check(frames>50,"Workbench was actually rendered");exiting=true;System.out.println("KITCHEN_CLIENT_GAMEPLAY_PASS ARMORY checks="+checks+" frames="+frames+" stages=4 portable=true");
            mc.tell(()->{try{mc.level.disconnect();mc.clearLevel(new net.minecraft.client.gui.screens.GenericDirtMessageScreen(net.minecraft.network.chat.Component.translatable("menu.savingLevel")));mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());check(mc.level==null,"Clean world exit");done=true;System.out.println("KITCHEN_CLIENT_PASS ARMORY checks="+checks+" frames="+frames+" stages=4 portable=true");}catch(Throwable t){fail(t);}});
        }catch(Throwable t){fail(t);}
    }
}
