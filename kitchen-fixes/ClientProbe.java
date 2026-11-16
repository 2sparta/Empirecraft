package ua.empirecraft.kitchenclienttest;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.impl.StationRegistry;
import com.pockyl.diegesis.impl.client.*;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.render.*;
import com.pockyl.handson_delight.station.*;
import umpaz.farmersrespite.common.block.entity.KettleBlockEntity;

/** Test-only addon: offline integrated world, real rendering and station transition code. Never joins a server. */
@Mod("empirecraft_kitchen_client_probe")
public final class ClientProbe {
    static final BlockPos KETTLE=new BlockPos(0,100,0), TABLE=new BlockPos(1,100,1);
    static final String[] STATIONS={"farmersrespite:kettle","farmersdelight:cooking_pot","farmersdelight:skillet","farmersdelight:stove"};
    final boolean legacy=getClass().getResource("/empirecraft-kitchen-legacy-fixture")!=null;
    String lastScreen="";boolean loaded,done;volatile boolean placed;volatile Throwable setupError;
    int ticks,steps,checks,frames;long boot=System.currentTimeMillis();
    public ClientProbe(){MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::frame);}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),c->{});}
    void fail(Throwable e){if(done)return;done=true;System.out.println("KITCHEN_CLIENT_FAIL "+e);e.printStackTrace();}
    void tick(TickEvent.ClientTickEvent event){
      if(event.phase!=TickEvent.Phase.END||done)return;
      try{
        var mc=Minecraft.getInstance();
        if(mc.screen!=null&&!mc.screen.getClass().getName().equals(lastScreen)){lastScreen=mc.screen.getClass().getName();System.out.println("KITCHEN_CLIENT_SCREEN "+lastScreen);}
        if(loaded&&mc.level==null&&(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen || mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen)){
          for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b){System.out.println("KITCHEN_CLIENT_CONFIRM "+b.getMessage().getString());b.onPress();break;}
        }
        if(!loaded&&mc.screen!=null&&mc.screen.getClass().getName().endsWith("EmpireHome")){
          loaded=true;System.out.println("KITCHEN_CLIENT_LOADING_OFFLINE_WORLD");mc.createWorldOpenFlows().loadLevel(mc.screen,"KITCHEN-TEST");return;
        }
        if(mc.level==null||mc.player==null){if(loaded&&System.currentTimeMillis()-boot>300000){capture("01-world-load-timeout");throw new AssertionError("Offline world not entered, screen="+lastScreen);}return;}
        if(!placed){
          if(ticks++==0)mc.getSingleplayerServer().execute(()->{try{setup(mc);}catch(Throwable e){setupError=e;}finally{placed=true;}});
          return;
        }
        if(setupError!=null)throw new RuntimeException("Fixture setup",setupError);
        ticks++;
        if(ticks<160)return;
        check(mc.getSingleplayerServer()!=null,"No remote server used");
        if(ticks==160){
          check(mc.level.getBlockEntity(KETTLE) instanceof KettleBlockEntity,"Client receives kettle");
          capture("01-cannons-before-focus");
          var first=station(0);FocusController.get().begin(first,false);System.out.println("KITCHEN_CLIENT_BEGIN");return;
        }
        if(ticks>200&&ticks%35==0&&steps<12){
          int target=(++steps)%STATIONS.length;
          FocusController.get().goOver(station(target),false,.35f);
          System.out.println("KITCHEN_CLIENT_HOP "+steps+" target="+target);
        }
        if(ticks==265||ticks==335||ticks==440)capture("02-transition-"+ticks);
        if(steps>=12&&ticks>660){
          check(frames>30,"World frames actually rendered");capture("03-after-twelve-hops");
          FocusController.get().requestExit();
          mc.clearLevel(new net.minecraft.client.gui.screens.TitleScreen());check(mc.level==null,"Clean world exit restores frozen registries");
          done=true;System.out.println("KITCHEN_CLIENT_PASS checks="+checks+" frames="+frames+" hops="+steps+" cannons=culverin,mortar legacy_migration="+legacy);
        }
      }catch(Throwable e){fail(e);}
    }
    ClientStation station(int index){var mc=Minecraft.getInstance();var pos=KETTLE.east(index);return ClientStations.get().getOrCreate(StationRegistry.find(mc.level.getBlockState(pos)).orElseThrow(),pos,Direction.NORTH);}
    void frame(TickEvent.RenderTickEvent event){
      if(done||event.phase!=TickEvent.Phase.END||ticks<210)return;
      try{
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){if(loaded&&System.currentTimeMillis()-boot>300000){capture("01-world-load-timeout");throw new AssertionError("Offline world not entered, screen="+lastScreen);}return;}frames++;
        check(!ClearBlocks.hides(TABLE),"LSO sewing table stays visible across station hops");
        var view=DiegesisClient.station(KETTLE).orElse(null);
        if(view!=null&&!view.isInUse()){
          check(view.isBlockHidden(),"Filled idle kettle retains its scene");
          check(view.node("drink").orElseThrow().isVisible(),"Idle kettle water stays visible");
        }
        var focus=FocusController.get().station();
        if(focus!=null&&focus.type().hidesBlock())check(focus.isBlockHidden(),"Scene replacing a native model remains during handoff");
        if(focus!=null&&!focus.type().hidesBlock())check(!HiddenBlocks.isHidden(focus.pos()),"Native pot/stove model remains during scene handoff");
      }catch(Throwable e){fail(e);}
    }
    void setup(Minecraft mc){
      var server=mc.getSingleplayerServer();var level=server.overworld();var player=server.getPlayerList().getPlayers().get(0);
      if(legacy){
        var nbt=new net.minecraft.nbt.CompoundTag();nbt.putString("id","tfc:food/cooked_egg");nbt.putByte("Count",(byte)17);var custom=new net.minecraft.nbt.CompoundTag();custom.putBoolean("empirecraft_legacy_fixture",true);nbt.put("tag",custom);
        var stack=ItemStack.of(nbt);check(stack.is(ForgeRegistries.ITEMS.getValue(new ResourceLocation("naturalist:cooked_egg"))),"Saved TFC egg migrates to Naturalist after legacy registry load");check(stack.getCount()==17&&stack.getTag()!=null&&stack.getTag().getBoolean("empirecraft_legacy_fixture"),"Saved count and custom NBT survive egg migration");
      }
      net.dries007.tfc.config.TFCConfig.SERVER.grassSpawningRocksChance.set(1.0);
      player.setGameMode(GameType.CREATIVE);player.teleportTo(level,1.5,100,-4.5,0,10);
      level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
      for(int x=-8;x<=12;x++)for(int z=-8;z<=12;z++)level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE_BRICKS.defaultBlockState());
      for(int x=0;x<STATIONS.length;x++){
        Block block=Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(STATIONS[x])));BlockState state=block.defaultBlockState();
        if(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))state=state.setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH);
        if(state.hasProperty(BlockStateProperties.LIT))state=state.setValue(BlockStateProperties.LIT,true);
        var lid=KettleStation.lidProperty(state);if(lid!=null)state=state.setValue(lid,false);
        level.setBlockAndUpdate(KETTLE.east(x),state);
      }
      level.setBlockAndUpdate(TABLE,Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(new ResourceLocation("legendarysurvivaloverhaul:sewing_table"))).defaultBlockState());
      var kettle=(KettleBlockEntity)level.getBlockEntity(KETTLE);kettle.getFluidTank().fill(new FluidStack(Fluids.WATER,1000),IFluidHandler.FluidAction.EXECUTE);kettle.setChanged();level.sendBlockUpdated(KETTLE,kettle.getBlockState(),kettle.getBlockState(),3);
      for(int i=0;i<2;i++){
        String id="siegemachines:"+(i==0?"culverin":"mortar");var item=Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));check(item!=Items.AIR,"Cannon placement item exists "+id);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));var base=new BlockPos(i==0?-4:6,99,4);
        var result=item.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(base).add(0,.5,0),Direction.UP,base,false)));
        check(result.consumesAction(),"Real cannon item placement succeeds "+id);System.out.println("KITCHEN_CLIENT_PLACED "+id);
      }
      player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
    }
}
