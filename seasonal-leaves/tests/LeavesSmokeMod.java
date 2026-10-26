package ua.empirecraft.leaves.test;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import sereneseasons.season.SeasonHandler;
import sereneseasons.api.season.SeasonHelper;

@Mod("empirecraft_leaves_test")
public final class LeavesSmokeMod {
    static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "empirecraft_leaves_test");
    static final RegistryObject<Block> MOD_LEAF = BLOCKS.register("test_leaves", () -> new LeavesBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)));
    static final RegistryObject<Block> MOD_FLOWER=BLOCKS.register("wild_flower",()->new FlowerBlock(MobEffects.LUCK,5,BlockBehaviour.Properties.copy(Blocks.DANDELION)));
    static final RegistryObject<Block> MOD_TALL=BLOCKS.register("tall_flower",()->new TallFlowerBlock(BlockBehaviour.Properties.copy(Blocks.LILAC)));
    static final RegistryObject<Block> MOD_BERRY=BLOCKS.register("berry",()->new SweetBerryBushBlock(BlockBehaviour.Properties.copy(Blocks.SWEET_BERRY_BUSH)));
    static final DeferredRegister<Block> HEMP_BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"nirvana");
    static final DeferredRegister<Block> COFFEE_BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"rusticdelight");
    static final RegistryObject<Block> HEMP=HEMP_BLOCKS.register("wild_hemp",()->new FlowerBlock(MobEffects.LUCK,5,BlockBehaviour.Properties.copy(Blocks.DANDELION)));
    static final RegistryObject<Block> COFFEE=COFFEE_BLOCKS.register("wild_coffee",()->new FlowerBlock(MobEffects.LUCK,5,BlockBehaviour.Properties.copy(Blocks.DANDELION)));
    VegetationScenario vegetation;
    MinecraftServer server; int ticks;
    boolean spring = Boolean.getBoolean("leaves.spring");
    boolean persistentMode = Boolean.getBoolean("leaves.persistent");
    static BlockPos p(int x,int y,int z) { return new BlockPos(x,y,z); }
    public LeavesSmokeMod() {
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        HEMP_BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        COFFEE_BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    void started(ServerStartedEvent e) {
        server=e.getServer(); var l=server.overworld();
        if(Boolean.getBoolean("vegetation.test")){vegetation=new VegetationScenario(server,spring);return;}
        l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
        for(int x=-1;x<=2;x++) for(int z=-1;z<=1;z++) l.setChunkForced(x,z,true);
        if(!spring) {
            for(int x=0;x<40;x++) for(int y=79;y<86;y++) for(int z=0;z<5;z++) l.setBlock(p(x,y,z),Blocks.AIR.defaultBlockState(),2);
            for(int x:new int[]{14,22,28,34}) {
                l.setBlock(p(x,80,2),Blocks.OAK_LOG.defaultBlockState(),2);
                l.setBlock(p(x,81,2),Blocks.OAK_LOG.defaultBlockState(),2);
            }
            for(int x=14;x<=17;x++) l.setBlock(p(x,82,2),Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.DISTANCE,x-13),2);
            l.setBlock(p(22,82,2),MOD_LEAF.get().defaultBlockState().setValue(BlockStateProperties.DISTANCE,1),2);
            l.setBlock(p(28,82,2),Blocks.BIRCH_LEAVES.defaultBlockState().setValue(BlockStateProperties.DISTANCE,1),2);
            l.setBlock(p(34,82,2),Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,true),2);
            l.setBlock(p(38,82,2),Blocks.OAK_LEAVES.defaultBlockState(),2); // Unsupported: must stay untouched.
            l.setBlock(p(35,82,2),Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true),2);
        }
    }
    void check(boolean ok,String msg) { if(!ok) throw new AssertionError(msg); }
    void tick(TickEvent.ServerTickEvent e) {
        if(server==null || e.phase!=TickEvent.Phase.START)return;
        ticks++; var l=server.overworld();
        if(vegetation!=null){
            vegetation.season();
            if(ticks==400){
                try{vegetation.verify();if(spring)vegetation.startGrowth();}catch(Throwable t){System.out.println("VEGETATION_SMOKE_FAIL "+t);t.printStackTrace();server.halt(false);server=null;return;}
                if(!spring){server.halt(false);server=null;}
            }
            if(ticks==600 && spring){
                try{vegetation.verifyGrowth();}catch(Throwable t){System.out.println("VEGETATION_SMOKE_FAIL "+t);t.printStackTrace();}
                server.halt(false);server=null;
            }
            return;
        }
        int duration=SeasonHelper.getSeasonState(l).getSubSeasonDuration();
        SeasonHandler.getSeasonSavedData(l).seasonCycleTicks=duration*(spring?3:10);
        try {
            if(ticks==350) {
                if(!spring) {
                    for(int x=14;x<=17;x++) check(l.getBlockState(p(x,82,2)).isAir(),"cross-chunk crown sheds x="+x);
                    check(l.getBlockState(p(22,82,2)).isAir(),"third-party leaf sheds");
                    check(l.getBlockState(p(28,82,2)).isAir(),"birch sheds");
                    check(persistentMode ? l.getBlockState(p(34,82,2)).isAir() : l.getBlockState(p(34,82,2)).is(Blocks.OAK_LEAVES),"persistent leaves follow config");
                    check(l.getBlockState(p(38,82,2)).is(Blocks.OAK_LEAVES),"unsupported leaves protected");
                    check(l.getBlockState(p(35,82,2)).getValue(BlockStateProperties.WATERLOGGED),"waterlogged leaves protected");
                    l.setBlock(p(17,82,2),Blocks.STONE.defaultBlockState(),2);
                    l.setBlock(p(28,81,2),Blocks.AIR.defaultBlockState(),2);
                    l.setBlock(p(28,80,2),Blocks.AIR.defaultBlockState(),2);
                } else {
                    for(int x=14;x<=16;x++) {
                        var s=l.getBlockState(p(x,82,2));
                        check(s.is(Blocks.OAK_LEAVES),"restored after restart x="+x);
                        check(s.getValue(BlockStateProperties.DISTANCE)==x-13,"original properties restored");
                    }
                    check(l.getBlockState(p(22,82,2)).is(MOD_LEAF.get()),"third-party leaf restored");
                    check(l.getBlockState(p(17,82,2)).is(Blocks.STONE),"building not overwritten");
                    check(l.getBlockState(p(34,82,2)).is(Blocks.OAK_LEAVES) && l.getBlockState(p(34,82,2)).getValue(BlockStateProperties.PERSISTENT), "persistent properties survive seasons");
                    check(l.getBlockState(p(28,82,2)).isAir(),"felled tree not resurrected");
                }
                System.out.println("LEAVES_SMOKE_PASS phase="+(spring?"spring":"winter"));
                server.halt(false);server=null;
            }
        } catch(Throwable t) {System.out.println("LEAVES_SMOKE_FAIL "+t);t.printStackTrace();server.halt(false);server=null;}
    }
}
