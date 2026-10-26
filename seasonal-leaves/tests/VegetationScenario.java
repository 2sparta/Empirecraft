package ua.empirecraft.leaves.test;
import java.util.UUID;
import java.util.List;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHandler;
import ua.empirecraft.leaves.VegetationPolicy;
import ua.empirecraft.leaves.SeasonalVegetation;
final class VegetationScenario {
    final MinecraftServer server;final ServerLevel l;final boolean summer;int rareX;
    static BlockPos p(int x){return new BlockPos(x,81,2);}
    VegetationScenario(MinecraftServer server,boolean summer){
        this.server=server;this.l=server.overworld();this.summer=summer;
        l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
        for(int x=-1;x<=2;x++)for(int z=-1;z<=1;z++)l.setChunkForced(x,z,true);
        int cx=4;while(!VegetationPolicy.rareChunk(l.getSeed(),cx,0,128))cx++;
        rareX=cx*16+2;l.setChunkForced(cx,0,true);l.getChunk(cx,0);
        if(!summer){
            for(int x=0;x<40;x++)for(int z=0;z<8;z++){
                l.setBlock(new BlockPos(x,80,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
                for(int y=81;y<85;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            for(int x=rareX;x<rareX+3;x++){
                l.setBlock(p(x).below(),Blocks.GRASS_BLOCK.defaultBlockState(),2);
                l.setBlock(p(x),Blocks.AIR.defaultBlockState(),2);
            }
            l.setBlock(p(2),Blocks.GRASS.defaultBlockState(),2);
            DoublePlantBlock.placeAt(l,Blocks.TALL_GRASS.defaultBlockState(),p(4),2);
            l.setBlock(p(6),LeavesSmokeMod.MOD_FLOWER.get().defaultBlockState(),2);
            DoublePlantBlock.placeAt(l,LeavesSmokeMod.MOD_TALL.get().defaultBlockState(),p(8),2);
            l.setBlock(p(10).below(),Blocks.FARMLAND.defaultBlockState(),2);
            l.setBlock(p(10),Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE,7),2);
            var player=new ServerPlayer(server,l,new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000042"),"VegetationTest"));
            var snapshot=BlockSnapshot.create(l.dimension(),l,p(12));
            l.setBlock(p(12),LeavesSmokeMod.MOD_FLOWER.get().defaultBlockState(),2);
            MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot,Blocks.GRASS_BLOCK.defaultBlockState(),player));
            var lowerSnapshot=BlockSnapshot.create(l.dimension(),l,p(16));
            var upperSnapshot=BlockSnapshot.create(l.dimension(),l,p(16).above());
            DoublePlantBlock.placeAt(l,LeavesSmokeMod.MOD_TALL.get().defaultBlockState(),p(16),2);
            MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityMultiPlaceEvent(List.of(lowerSnapshot,upperSnapshot),Blocks.GRASS_BLOCK.defaultBlockState(),player));
            l.setBlock(p(14),LeavesSmokeMod.MOD_BERRY.get().defaultBlockState().setValue(SweetBerryBushBlock.AGE,2),2);
            l.setBlock(p(rareX),LeavesSmokeMod.HEMP.get().defaultBlockState(),2);
            l.setBlock(p(rareX+1),LeavesSmokeMod.COFFEE.get().defaultBlockState(),2);
        }
    }
    void season(){int d=SeasonHelper.getSeasonState(l).getSubSeasonDuration();SeasonHandler.getSeasonSavedData(l).seasonCycleTicks=d*(summer?4:10);}
    static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    void startGrowth() throws Exception {
        Class<?> config=Class.forName("ua.empirecraft.leaves.VegetationConfig");
        var spawn=config.getDeclaredField("SPAWN");spawn.setAccessible(true);
        ((net.minecraftforge.common.ForgeConfigSpec.BooleanValue)spawn.get(null)).set(true);
    }
    void verifyGrowth(){
        int count=0;for(int x=16;x<32;x++)for(int z=0;z<16;z++)if(l.getBlockState(new BlockPos(x,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x,z)-1,z)).is(Blocks.GRASS))count++;
        check(count>0 && count<=8,"bounded spontaneous seasonal grass: "+count);
        check(!SeasonalVegetation.allowRenewal(l,rareX>>4,0,LeavesSmokeMod.HEMP.get().defaultBlockState()),"rare cooldown remains after immediate harvest");
        System.out.println("VEGETATION_GROWTH_PASS new grass="+count);
    }
    void verify(){
        if(!summer){
            for(int x:new int[]{2,4,6,8,14})check(l.getBlockState(p(x)).isAir(),"winter hides ground plant x="+x);
            check(l.getBlockState(p(4).above()).isAir()&&l.getBlockState(p(8).above()).isAir(),"both upper halves hidden");
            check(l.getBlockState(p(10)).is(Blocks.CARROTS),"cultivated crop preserved");
            check(l.getBlockState(p(12)).is(LeavesSmokeMod.MOD_FLOWER.get()),"player flower protected");
            check(l.getBlockState(p(16)).is(LeavesSmokeMod.MOD_TALL.get()) && l.getBlockState(p(16).above()).is(LeavesSmokeMod.MOD_TALL.get()),"player two-block flower protected");
            check(l.getBlockState(p(rareX)).isAir()&&l.getBlockState(p(rareX+1)).isAir(),"rare plants dormant in winter");
            check(!SeasonalVegetation.allowRenewal(l,rareX>>4,0,LeavesSmokeMod.HEMP.get().defaultBlockState()),"winter candidate forbidden");
            l.setBlock(p(6),Blocks.STONE.defaultBlockState(),2);
            l.setBlock(p(4).below(),Blocks.STONE.defaultBlockState(),2);
        }else{
            check(l.getBlockState(p(2)).is(Blocks.GRASS),"grass restored after restart");
            check(l.getBlockState(p(8)).is(LeavesSmokeMod.MOD_TALL.get())&&l.getBlockState(p(8).above()).is(LeavesSmokeMod.MOD_TALL.get()),"two-block mod plant restored");
            check(l.getBlockState(p(14)).is(LeavesSmokeMod.MOD_BERRY.get())&&l.getBlockState(p(14)).getValue(SweetBerryBushBlock.AGE)==2,"original mod age preserved");
            check(l.getBlockState(p(6)).is(Blocks.STONE),"replacement building preserved");
            check(l.getBlockState(p(4)).isAir()&&l.getBlockState(p(4).above()).isAir(),"changed soil cancels both halves");
            int rare=0;for(int x=rareX;x<rareX+2;x++)if(!l.getBlockState(p(x)).isAir())rare++;
            check(rare==1,"one combined rare plant per habitat chunk: "+rare);
            check(!SeasonalVegetation.allowRenewal(l,rareX>>4,0,LeavesSmokeMod.HEMP.get().defaultBlockState()),"existing rare plant blocks another renewal");
            int denied=4;while(VegetationPolicy.rareChunk(l.getSeed(),denied,0,128))denied++;
            l.setChunkForced(denied,0,true);l.getChunk(denied,0);
            check(!SeasonalVegetation.allowRenewal(l,denied,0,LeavesSmokeMod.HEMP.get().defaultBlockState()),"non-habitat chunk rejects hemp");
            try{
                Class<?> config=Class.forName("ua.empirecraft.leaves.VegetationConfig");var days=config.getDeclaredField("RARE_DAYS");days.setAccessible(true);
                ((net.minecraftforge.common.ForgeConfigSpec.IntValue)days.get(null)).set(7);
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
            l.setBlock(p(rareX),Blocks.AIR.defaultBlockState(),2);l.setBlock(p(rareX+1),Blocks.AIR.defaultBlockState(),2);
            l.setBlock(p(rareX+2),LeavesSmokeMod.HEMP.get().defaultBlockState(),2);
            SeasonalVegetation.renewalComplete(l,rareX>>4,0);
            l.setBlock(p(rareX+2),Blocks.AIR.defaultBlockState(),2);
            check(!SeasonalVegetation.allowRenewal(l,rareX>>4,0,LeavesSmokeMod.HEMP.get().defaultBlockState()),"immediate harvest cannot bypass seven-day cooldown");
        }
        System.out.println("VEGETATION_SMOKE_PASS phase="+(summer?"summer":"winter"));
    }
}
