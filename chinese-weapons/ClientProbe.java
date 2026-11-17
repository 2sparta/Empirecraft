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
    boolean loaded,done,queued,exiting,transferred,selectedByPacket,alternateRequested,catalogOpened;int ticks,checks,stage=-1,wait,frames,catalogFrames;volatile boolean ready;volatile Throwable error;
    public ClientProbe(){MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::frame);}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),c->{});}
    void fail(Throwable t){if(done)return;done=true;System.out.println("KITCHEN_CLIENT_FAIL ARMORY "+t);t.printStackTrace();}
    void frame(TickEvent.RenderTickEvent event){if(event.phase==TickEvent.Phase.END&&!done){if(Minecraft.getInstance().screen instanceof WorkshopScreen)frames++;if(Minecraft.getInstance().screen instanceof ChineseCatalog)catalogFrames++;}}
    static boolean target(AssemblyRecipe recipe){return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(recipe.result.getItem()).toString().equals("chineseweapons:iron_mountain_character_armor_chestplate");}
    static final class ChineseCatalog extends net.minecraft.client.gui.screens.Screen {
        int page;
        final java.util.List<net.minecraft.world.entity.LivingEntity> models=new java.util.ArrayList<>();
        ChineseCatalog(){super(net.minecraft.network.chat.Component.literal("Ottoman armor model verification"));var mc=Minecraft.getInstance();
            for(var item:net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues())if(item instanceof ArmorItem armor&&net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).getNamespace().equals("chineseweapons")){var entity=new net.minecraft.world.entity.decoration.ArmorStand(mc.level,0,100,0);entity.setItemSlot(armor.getEquipmentSlot(),new ItemStack(item));entity.setYBodyRot(160);models.add(entity);}
            var horse=new net.minecraft.world.entity.animal.horse.Horse(net.minecraft.world.entity.EntityType.HORSE,mc.level);if(!horse.getSlot(401).set(new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("chineseweapons:iron_tang_dynasty_horse_armor")))))throw new AssertionError("Horse armor slot rejected");models.add(horse);
        }
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xff20232b);g.drawCenteredString(font,"Chinese Weapons: equipment models",width/2,5,0xf0d594);
            int cols=8,cellW=width/cols,cellH=(height-35)/3;for(int i=page*24;i<Math.min(models.size(),(page+1)*24);i++){int local=i-page*24;int x=cellW/2+(local%cols)*cellW,y=24+(local/cols)*cellH+cellH-6;net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(g,x,y,Math.min(25,cellH/2-3),0,0,models.get(i));}
            g.drawCenteredString(font,"Native models / layered armor / Forge 1.20.1",width/2,height-12,0xb4c0cb);
        }
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
            if(catalogOpened){if(++ticks<100)return;check(catalogFrames>40,"Chinese armor models actually rendered");var catalog=(ChineseCatalog)mc.screen;capture("03-chinese-armor-models-"+(catalog.page+1));if(catalog.page==0){catalog.page=1;ticks=0;catalogFrames=0;return;}finish();return;}
            if(!queued){queued=true;server(()->{
                var srv=mc.getSingleplayerServer();var level=srv.overworld();level.getChunkAt(pos);for(int x=-5;x<=7;x++)for(int z=-5;z<=5;z++)level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE_BRICKS.defaultBlockState());level.setBlockAndUpdate(pos,Armory.TABLE.get().defaultBlockState());
                var table=(WorkshopEntity)level.getBlockEntity(pos);var all=AssemblyRecipe.all(level);var r=all.stream().filter(ClientProbe::target).findFirst().orElseThrow();table.select(all.indexOf(r));supply(table,r,0);
                var player=srv.getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(GameType.CREATIVE);player.connection.teleport(.5,101,3.5,180,15);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);NetworkHooks.openScreen(player,table,pos);
            });return;}
            if(!ready||!(mc.player.containerMenu instanceof WorkshopMenu menu)||!(mc.screen instanceof WorkshopScreen))return;
            ticks++;
            if(ticks<80)return;
            check(!AssemblyRecipe.all(mc.level).isEmpty(),"Custom recipes synchronized to client");check(menu.selected()>=0,"Server-selected workpiece synchronized");
            if(!selectedByPacket){var list=AssemblyRecipe.all(mc.level);int last=list.size()-1;if(!alternateRequested){check(last>255,"Selection test exceeds the vanilla byte limit");ArmoryNetwork.select(menu.containerId,last);alternateRequested=true;wait=0;return;}if(menu.selected()!=last){if(++wait>180)throw new AssertionError("Large-index selection was not synchronized");return;}var target=list.stream().filter(ClientProbe::target).findFirst().orElseThrow();ArmoryNetwork.select(menu.containerId,list.indexOf(target));selectedByPacket=true;ticks=0;return;}
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
                if(stage==3&&menu.slots.get(2).hasItem()){check(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(menu.slots.get(2).getItem().getItem()).toString().equals("chineseweapons:iron_mountain_character_armor_chestplate"),"Real Chinese output reaches client");check(menu.slots.get(2).getItem().getTag().contains(Parts.DATA),"Installed component data synchronized");stage=4;capture("03-assembled-armor");ticks=0;}
                return;
            }
            if(ticks<100)return;
            check(frames>50,"Workbench was actually rendered");mc.player.closeContainer();var catalog=new ChineseCatalog();check(catalog.models.size()==40,"Every Chinese armor and horse armor item has a model fixture: "+catalog.models.size());mc.setScreen(catalog);catalogOpened=true;ticks=0;
        }catch(Throwable t){fail(t);}
    }
    void finish(){var mc=Minecraft.getInstance();exiting=true;System.out.println("KITCHEN_CLIENT_GAMEPLAY_PASS ARMORY checks="+checks+" frames="+frames+" stages=4 portable=true chinese_models=40");mc.tell(()->{try{mc.level.disconnect();mc.clearLevel(new net.minecraft.client.gui.screens.GenericDirtMessageScreen(net.minecraft.network.chat.Component.translatable("menu.savingLevel")));mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());check(mc.level==null,"Clean world exit");done=true;System.out.println("KITCHEN_CLIENT_PASS ARMORY checks="+checks+" frames="+frames+" stages=4 portable=true chinese_models=40");}catch(Throwable t){fail(t);}});}
}
