package ua.empirecraft.trmt.test;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import milkucha.trmt.TRMTConfig;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.trmt.*;

@Mod("empirecraft_trmt_test")
public final class SoilProbe {
    public SoilProbe() { MinecraftForge.EVENT_BUS.addListener(SoilProbe::run); }
    static void check(boolean good,String message){if(!good)throw new AssertionError(message);}
    static Method tick(Class<?> type){for(var method:type.getDeclaredMethods())if(method.getName().contains("trmt")&&method.getName().endsWith("onTick")){method.setAccessible(true);return method;}throw new AssertionError("Missing injected TRMT walking handler");}
    static BlockPos position(int i){return new BlockPos(512+(i%12)*3,75,512+(i/12)*3);}
    static void run(ServerStartedEvent event) {
        var server=event.getServer();var level=server.overworld();
        try {
            Class<?> info=Class.forName("org.valkyrienskies.mod.common.BlockStateInfo");
            Object provider=info.getField("INSTANCE").get(null);
            var get=info.getMethod("get",BlockState.class);
            System.out.println("SHIP_FLUID_MASS_DIAGNOSTIC water="+get.invoke(provider,Blocks.WATER.defaultBlockState())+" flowing="+get.invoke(provider,Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,7))+" air="+get.invoke(provider,Blocks.AIR.defaultBlockState()));
            var config=TRMTConfig.get();config.erosionThresholds.grass.min=config.erosionThresholds.grass.max=1;
            config.erosionThresholds.dirt.min=config.erosionThresholds.dirt.max=1;
            config.erosionThresholds.sand.min=config.erosionThresholds.sand.max=1;
            check(RegionalTrails.PATHS.size()==38,"Complete soil catalog");
            var player=FakePlayerFactory.getMinecraft(level);
            var cow=(Mob)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("minecraft:cow")).create(level);
            cow.setLeashedTo(player,false);
            Method pt=tick(ServerPlayer.class), mt=tick(Mob.class);
            var originals=new ArrayList<>(RegionalTrails.PATHS.keySet());
            originals.sort(Comparator.comparing(b->ForgeRegistries.BLOCKS.getKey(b).toString()));
            Path saved=Path.of("soil-memory-probe.nbt");
            if(!Files.exists(saved)) {
                int i=0;
                CompoundTag expected=new CompoundTag();
                for(Block source:originals)for(boolean leashed:List.of(false,true)) {
                    BlockPos pos=position(i++);var path=RegionalTrails.PATHS.get(source);
                    level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
                    BlockState origin=source.defaultBlockState();
                    for(var prop:origin.getProperties()) if(prop instanceof BooleanProperty flag&&!flag.getName().equals("snowy"))origin=origin.setValue(flag,true);
                    level.setBlock(pos,origin,2);origin=level.getBlockState(pos);
                    level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
                    expected.put(pos.asLong()+"",NbtUtils.writeBlockState(origin));
                    var walker=leashed?cow:player;var method=leashed?mt:pt;
                    for(int crossing=0;crossing<18;crossing++) {
                        walker.setPos(pos.getX()+1.5,76,pos.getZ()+.5);walker.setOnGround(true);method.invoke(walker,new CallbackInfo("tick",false));
                        var before = level.getBlockState(pos);
                        double height = before.getCollisionShape(level, pos).max(Direction.Axis.Y);
                        walker.setPos(pos.getX()+.5,75+height,pos.getZ()+.5);walker.setOnGround(true);method.invoke(walker,new CallbackInfo("tick",false));
                    }
                    var actual=level.getBlockState(pos);check(actual.getBlock()==path,"Actual injected walking retains origin "+ForgeRegistries.BLOCKS.getKey(source)+" mob="+leashed);
                    check(actual.getValue(RememberedPath.WEAR)==4,"Full wear reached");
                    if (path.kind.equals("sand")) {
                        var vanilla = ((Block)milkucha.trmt.TRMTBlocks.ERODED_SAND.get()).defaultBlockState();
                        for (int wear=0; wear<5; wear++) {
                            var expectedSand = vanilla.setValue(milkucha.trmt.block.ErodedSandBlock.STAGE, wear);
                            var regionalSand = actual.setValue(RememberedPath.WEAR, wear);
                            check(expectedSand.getCollisionShape(level,pos).bounds().equals(regionalSand.getCollisionShape(level,pos).bounds()),"Sand collision parity at stage "+wear);
                            check(expectedSand.getShape(level,pos).bounds().equals(regionalSand.getShape(level,pos).bounds()),"Sand outline parity at stage "+wear);
                        }
                    }
                    var memory=level.getChunkAt(pos).getCapability(TrailMemory.CAP).orElseThrow(()->new AssertionError("cap"));
                    var entry=memory.entry(level,pos,actual,path);
                    check(memory.restore(level,entry,path).equals(origin),"All original state properties retained");
                    float pressure=entry.walked;
                    method.invoke(walker,new CallbackInfo("tick",false));
                    check(entry.walked==pressure,"Standing still does not erode");
                    var roundtrip=new TrailMemory();roundtrip.deserializeNBT(memory.serializeNBT());
                    check(roundtrip.restore(level,roundtrip.entry(level,pos,actual,path),path).equals(origin),"NBT capability round trip");
                }
                // Chunk boundary calls must not generate a neighboring chunk.
                var chunk=level.getChunkSource().getChunkNow(32000,32000);check(chunk==null,"Unloaded sentinel before");
                RegionalTrails.step(level,new BlockPos(512000,75,512000),10,Direction.NORTH);
                check(level.getChunkSource().getChunkNow(32000,32000)==null,"No forced chunk load");
                menuIcons();
                NbtIo.writeCompressed(expected,saved.toFile());
                server.saveEverything(false,true,true);
                System.out.println("SOIL_MEMORY_PHASE1_PASS walking_cases="+i+" noForcedChunks=true exactState=true menuIcons=true");
            } else {
                CompoundTag expected=NbtIo.readCompressed(saved.toFile());
                int i=0;
                config.deErosionTimeoutDays.grass.stage1=config.deErosionTimeoutDays.grass.stage2=config.deErosionTimeoutDays.grass.stage3=config.deErosionTimeoutDays.grass.stage4=config.deErosionTimeoutDays.grass.stage5=0;
                config.deErosionTimeoutDays.sand.stage1=config.deErosionTimeoutDays.sand.stage2=config.deErosionTimeoutDays.sand.stage3=config.deErosionTimeoutDays.sand.stage4=config.deErosionTimeoutDays.sand.stage5=0;
                config.deErosionTimeoutDays.dirt.erodedDirt=0;
                for(Block source:originals)for(boolean ignored:List.of(false,true)) {
                    BlockPos pos=position(i++);var chunk=level.getChunkAt(pos);var state=level.getBlockState(pos);
                    var path=RegionalTrails.PATHS.get(source);check(state.getBlock()==path,"Path loaded after JVM restart");
                    var memory=chunk.getCapability(TrailMemory.CAP).orElseThrow(()->new AssertionError("cap"));
                    var e=memory.entry(level,pos,state,path);
                    check(e.original.equals(expected.getCompound(pos.asLong()+"")),"Chunk disk state persisted "+pos);
                    for(int stage=0;stage<5;stage++) {
                        e.last=level.getGameTime()-2;
                        path.randomTick(level.getBlockState(pos),level,pos,level.random);
                    }
                    check(level.getBlockState(pos).getBlock()==source,"Regrows exact source "+source);
                    check(!memory.serializeNBT().contains(pos.asLong()+""),"Restored memory removed");
                }
                BlockPos pos=position(0);Block source=originals.get(0);var path=RegionalTrails.PATHS.get(source);
                RegionalTrails.step(level,pos,100,Direction.NORTH);
                level.setBlock(pos,Blocks.GOLD_BLOCK.defaultBlockState(),3);
                check(!level.getChunkAt(pos).getCapability(TrailMemory.CAP).orElseThrow(()->new AssertionError("cap")).serializeNBT().contains(pos.asLong()+""),"Building/break removes history");
                check(level.getBlockState(pos).is(Blocks.GOLD_BLOCK),"Building untouched");
                // Different dimensions have separate capabilities even for identical coordinates.
                if(server.getLevel(net.minecraft.world.level.Level.NETHER)!=null){var other=server.getLevel(net.minecraft.world.level.Level.NETHER);var cap=other.getChunkAt(pos).getCapability(TrailMemory.CAP).orElseThrow(()->new AssertionError("cap"));check(!cap.serializeNBT().contains(pos.asLong()+""),"Dimension isolated");}
                System.out.println("SOIL_MEMORY_PHASE2_PASS restart_cases="+i+" regrowth=true buildRemoval=true dimensions=true");
            }
        } catch(Throwable ex) {System.out.println("SOIL_MEMORY_FAIL");ex.printStackTrace();}
        finally {server.halt(false);}
    }
    static void menuIcons() throws Exception {
        Class<?> bukkit=Class.forName("org.bukkit.Bukkit");Object manager=bukkit.getMethod("getPluginManager").invoke(null);
        Object plugin=manager.getClass().getMethod("getPlugin",String.class).invoke(manager,"EmpirecraftFeudal");
        check(plugin!=null,"Current Feudal installed for icon test");
        Class<?> helper=Class.forName("ua.empirecraft.feudal.MenuIcons",true,plugin.getClass().getClassLoader());
        Class<?> material=Class.forName("org.bukkit.Material");
        Method id=helper.getDeclaredMethod("id",material),item=helper.getDeclaredMethod("item",material);id.setAccessible(true);item.setAccessible(true);
        var values=(Object[])material.getMethod("values").invoke(null);int tested=0;
        for(Object value:values){String name=value.toString();if(!List.of("BELL","PLAYER_HEAD","GRASS_BLOCK","GOLD_INGOT","IRON_HELMET","WRITABLE_BOOK","WHITE_BANNER","GOLDEN_HELMET","RED_BED","PAPER","BREAD","CLOCK","ARROW","SUNFLOWER","BARRIER","MAP","RED_DYE","LIME_DYE","STONE_BRICKS","FEATHER","EXPERIENCE_BOTTLE","GRAY_DYE","CHEST","WHEAT","ROTTEN_FLESH").contains(name))continue;
            String expected=(String)id.invoke(null,value);var key=new ResourceLocation(expected);
            check(ForgeRegistries.ITEMS.containsKey(key),"Icon exists "+name+" -> "+key);
            Object result=item.invoke(null,value);Object actualType=result.getClass().getMethod("getType").invoke(result);
            check(!actualType.equals(value),"Uses mod item instead of vanilla "+name+" got="+actualType);tested++;
        }
        check(tested==25,"All mod icon roles");System.out.println("MENU_MOD_ICONS_PASS roles="+tested);
    }
}
