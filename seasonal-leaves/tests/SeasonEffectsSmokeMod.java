package ua.empirecraft.leaves.test;

import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHandler;
import ua.empirecraft.leaves.*;

/** Test-only mod: all state is in the runner's disposable world. */
@Mod("empirecraft_effects_test")
public final class SeasonEffectsSmokeMod {
    MinecraftServer server;ServerLevel level;int ticks,checks,manualClear;
    final boolean resumed=Boolean.getBoolean("effects.resume");
    static final BlockPos SUPPORT=new BlockPos(3,65,1),OTHER=new BlockPos(10,65,1),STEP=new BlockPos(4,65,1);
    public SeasonEffectsSmokeMod() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,this::seasonTick);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    ServerLevelData data() {return (ServerLevelData)level.getLevelData();}
    void winter() {int d=SeasonHelper.getSeasonState(level).getSubSeasonDuration();SeasonHandler.getSeasonSavedData(level).seasonCycleTicks=10*d;}
    void seasonTick(TickEvent.LevelTickEvent e) {if(level!=null&&e.level==level&&e.phase==TickEvent.Phase.END)winter();}
    void started(ServerStartedEvent event) {
        server=event.getServer();level=server.overworld();
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);winter();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setChunkForced(x,z,true);
        if(resumed)return;
        level.setBlock(SUPPORT.west(),Blocks.OAK_LOG.defaultBlockState(),2);
        level.setBlock(SUPPORT,Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,7),2);
        level.setBlock(OTHER.west(),Blocks.OAK_LOG.defaultBlockState(),2);
        level.setBlock(OTHER,Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,1),2);
        jump(66);level.setWeatherParameters(0,1200,true,false);
    }
    void jump(double y) {
        var fake=FakePlayerFactory.getMinecraft(level);fake.setPos(3.5,y,1.5);
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(fake));
    }
    void tick(TickEvent.ServerTickEvent event) {
        if(server==null||event.phase!=TickEvent.Phase.END)return;
        try {
            ticks++;
            if(resumed&&ticks==40) {
                long expected=Long.parseLong(Files.readString(server.getWorldPath(LevelResource.ROOT).resolve("weather-end-proof.txt")).trim());
                long actual=SeasonWeather.state(level).save(new CompoundTag()).getLong("end");
                check(actual==expected,"storm endpoint must survive restart without re-randomization or extension");
                check(data().isRaining()&&data().getRainTime()>57600,"long storm is still running after restart");
                finish();return;
            }
            if(resumed)return;
            if(ticks==15) {
                check(LeafSupportGuard.protectedAt(level,SUPPORT),"jump protects leaf support immediately");
                level.setBlock(SUPPORT,Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,7),2);
                level.getBlockState(SUPPORT).randomTick(level,SUPPORT,level.getRandom());
                check(level.getBlockState(SUPPORT).is(Blocks.OAK_LEAVES),"native leaf decay cannot break a jumping player's support");
            }
            if(ticks==25)jump(67.2);
            if(ticks==16) {
                level.setBlock(STEP,Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,2),2);
                var fake=FakePlayerFactory.getMinecraft(level);fake.setPos(4.5,66,1.5);
                MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,fake));
                check(LeafSupportGuard.protectedAt(level,STEP),"movement onto adjacent leaf is protected before the next periodic scan");
            }
            if(ticks==30) {
                check(level.getBlockState(SUPPORT).is(Blocks.OAK_LEAVES),"seasonal removal must retain support during jump");
                check(level.getBlockState(OTHER).isAir(),"unoccupied tree continues seasonal shedding");
                check(data().isRaining()&&data().getRainTime()>=2*57600-40&&data().getRainTime()<=5*57600,"automatic winter storm is 2-5 calendar days");
                System.out.println("EFFECTS_WEATHER_DURATION "+data().getRainTime());
                check(!server.getCommands().getDispatcher().getRoot().getChild("seasonweather").canUse(server.createCommandSourceStack().withPermission(0)),"weather controls require admin permission");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"weather clear 1000");
                manualClear=data().getClearWeatherTime();check(manualClear>0,"native weather command establishes a clear interval");
            }
            if(ticks==60) {
                check(level.getBlockState(SUPPORT).is(Blocks.OAK_LEAVES),"support stays intact throughout jump grace period");
                System.out.println("EFFECTS_MANUAL_WEATHER initial="+manualClear+" remaining="+data().getClearWeatherTime()+" raining="+data().isRaining());
                check(!data().isRaining()&&data().getClearWeatherTime()>=manualClear-40,"manual /weather clear is honored");
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);
                level.setWeatherParameters(0,100,true,true);
            }
            if(ticks==90) {
                check(data().isRaining()&&data().getRainTime()==100,"doWeatherCycle=false leaves native weather untouched");
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true,server);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"seasonweather auto");
            }
            if(ticks==180) {
                check(level.getBlockState(SUPPORT).isAir(),"seasonal shedding resumes after player leaves");
                check(data().getRainTime()>2*57600-200,"automatic winter weather resumes");
                long end=SeasonWeather.state(level).save(new CompoundTag()).getLong("end");
                Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("weather-end-proof.txt"),Long.toString(end));
                finish();
            }
        }catch(Throwable failure){failure.printStackTrace();System.out.println("EFFECTS_SMOKE_FAIL "+failure);server.halt(false);server=null;}
    }
    void finish() {System.out.println("EFFECTS_SMOKE_PASS phase="+(resumed?"restart":"initial")+" assertions="+checks);server.halt(false);server=null;}
}
