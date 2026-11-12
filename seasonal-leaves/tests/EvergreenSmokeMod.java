package ua.empirecraft.leaves.test;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHandler;
import ua.empirecraft.leaves.SeasonalLeaves;

/** Two real server boots: 1.3.1 loses evergreen leaves; 1.3.2 repairs its saved backups. */
@Mod("empirecraft_evergreen_test")
public final class EvergreenSmokeMod {
    final boolean legacy=Boolean.getBoolean("evergreen.legacy");
    final Map<BlockPos,BlockState> trees=new LinkedHashMap<>();
    final List<BlockPos> deciduous=new ArrayList<>();
    final BlockPos occupied=new BlockPos(3,65,39),cut=new BlockPos(7,65,39),empty=new BlockPos(11,65,39);
    MinecraftServer server;ServerLevel level;int ticks,checks;
    public EvergreenSmokeMod() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,this::seasonTick);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    void winter() {int d=SeasonHelper.getSeasonState(level).getSubSeasonDuration();SeasonHandler.getSeasonSavedData(level).seasonCycleTicks=10*d;}
    void seasonTick(TickEvent.LevelTickEvent e) {if(level!=null&&e.level==level&&e.phase==TickEvent.Phase.END)winter();}
    BlockState leaf(Block b,int i) {
        var s=b.defaultBlockState();
        if(s.hasProperty(BlockStateProperties.DISTANCE))s=s.setValue(BlockStateProperties.DISTANCE,1);
        if(s.hasProperty(BlockStateProperties.PERSISTENT))s=s.setValue(BlockStateProperties.PERSISTENT,i%2==0);
        return s;
    }
    void plant(BlockPos p,BlockState s) {level.setBlock(p.west(),Blocks.OAK_LOG.defaultBlockState(),2);level.setBlock(p,s,2);}
    void started(ServerStartedEvent e) {
        try {
            server=e.getServer();level=server.overworld();winter();
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
            for(int x=-1;x<=3;x++)for(int z=-1;z<=3;z++)level.setChunkForced(x,z,true);
            var ids=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/evergreen-fixture.json"))).getAsJsonObject().getAsJsonArray("values");
            int i=0;
            for(var entry:ids) {
                var id=new ResourceLocation(entry.isJsonPrimitive()?entry.getAsString():entry.getAsJsonObject().get("id").getAsString());
                if(!BuiltInRegistries.BLOCK.containsKey(id))continue;
                Block b=BuiltInRegistries.BLOCK.get(id);BlockPos p=new BlockPos(3+(i%8)*4,65,3+(i/8)*4);
                BlockState s=leaf(b,i++);trees.put(p,s);
                if(legacy)plant(p,s);
                else check(SeasonalLeaves.isEvergreen(s),"Missing evergreen tag: "+id);
            }
            check(i>=14,"Installed evergreen tree coverage");
            i=0;
            for(String id:List.of("minecraft:oak_leaves","minecraft:birch_leaves","minecraft:cherry_leaves","ecologics:walnut_leaves","fruitsdelight:apple_leaves","fruitsdelight:peach_leaves","vinery:dark_cherry_leaves")) {
                var key=new ResourceLocation(id);if(!BuiltInRegistries.BLOCK.containsKey(key))continue;
                BlockPos p=new BlockPos(3+(i++*4),65,35);deciduous.add(p);
                if(legacy)plant(p,leaf(BuiltInRegistries.BLOCK.get(key),1));
                else check(!SeasonalLeaves.isEvergreen(BuiltInRegistries.BLOCK.get(key).defaultBlockState()),"Deciduous tree incorrectly protected: "+id);
            }
            if(legacy) {plant(occupied,leaf(Blocks.SPRUCE_LEAVES,1));plant(cut,leaf(Blocks.SPRUCE_LEAVES,1));}
            else {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"plantgrowth off");
                level.setBlock(occupied,Blocks.STONE.defaultBlockState(),2);
                level.setBlock(cut.west(),Blocks.AIR.defaultBlockState(),2);
                level.setBlock(empty.west(),Blocks.OAK_LOG.defaultBlockState(),2);
                for(var tree:trees.entrySet())if(!SeasonalLeaves.isLeaves(tree.getValue()))plant(tree.getKey(),tree.getValue());
            }
            var report=new JsonArray();
            var evergreen=TagKey.create(Registries.BLOCK,new ResourceLocation(SeasonalLeaves.ID,"evergreen"));
            for(var b:BuiltInRegistries.BLOCK)if(SeasonalLeaves.isLeaves(b.defaultBlockState())||b.defaultBlockState().is(evergreen)) {
                var row=new JsonObject();row.addProperty("id",BuiltInRegistries.BLOCK.getKey(b).toString());row.addProperty("evergreen",b.defaultBlockState().is(evergreen));report.add(row);
            }
            Files.writeString(Path.of("evergreen-registry.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Throwable t){fail(t);}
    }
    void tick(TickEvent.ServerTickEvent e) {
        if(server==null||e.phase!=TickEvent.Phase.END)return;
        try {
            ticks++;
            if(!legacy&&ticks==40) {
                check(level.getBlockState(new BlockPos(3,65,3)).isAir(),"Growth off must hold winter recovery");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"plantgrowth on");
            }
            if(ticks!=300)return;
            if(legacy) {
                check(level.getBlockState(new BlockPos(3,65,3)).isAir(),"1.3.1 regression reproduction: spruce disappears in winter");
                check(level.getBlockState(occupied).isAir()&&level.getBlockState(cut).isAir(),"Legacy backups exist for repair safety checks");
            } else {
                for(var tree:trees.entrySet())check(level.getBlockState(tree.getKey()).equals(tree.getValue()),"Evergreen restored/preserved with exact properties: "+tree);
                check(level.getBlockState(occupied).is(Blocks.STONE),"Repair must preserve player replacement");
                check(level.getBlockState(cut).isAir(),"Cut tree must not regrow from stale backup");
                check(level.getBlockState(empty).isAir(),"Unrecorded air must remain air");
                var p=new BlockPos(11,75,39);level.setBlock(p,Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,7),2);
                level.getBlockState(p).randomTick(level,p,level.random);
                check(level.getBlockState(p).isAir(),"Ordinary leaf decay after log removal must still work");
            }
            for(var p:deciduous)check(level.getBlockState(p).isAir(),"Deciduous leaf must still shed in winter at "+p);
            System.out.println("EVERGREEN_NATIVE_PASS phase="+(legacy?"legacy":"fixed")+" trees="+trees.size()+" deciduous="+deciduous.size()+" assertions="+checks);
            server.halt(false);server=null;
        }catch(Throwable t){fail(t);}
    }
    void fail(Throwable t) {t.printStackTrace();System.out.println("EVERGREEN_NATIVE_FAIL "+t);if(server!=null)server.halt(false);server=null;}
}
