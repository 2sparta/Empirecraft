package ua.empirecraft.packfixes.test;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("empirecraft_runtime_probe")
public final class RuntimeNativeProbe {
    private static int checks;
    private static void check(boolean result,String label){checks++;if(!result)throw new AssertionError(label);}
    public RuntimeNativeProbe(){MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event)->{
        var server=event.getServer();
        try{run(server);System.out.println("RUNTIME_NATIVE_PASS checks="+checks);}
        catch(Throwable error){System.out.println("RUNTIME_NATIVE_FAIL");error.printStackTrace();}
        server.halt(false);
    });}
    static void run(MinecraftServer server)throws Exception {
        check(Arrays.stream(HashMapPalette.class.getDeclaredMethods()).anyMatch(m->m.getName().contains("empirecraft$denseEntries")),"real palette Mixin applied");
        var palette=new HashMapPalette<>(Block.BLOCK_STATE_REGISTRY,4,null);
        var expected=List.of(Blocks.STONE.defaultBlockState(),Blocks.GRANITE.defaultBlockState(),Blocks.DIRT.defaultBlockState(),Blocks.AIR.defaultBlockState());
        for(var state:expected)palette.idFor(state);
        check(palette.getEntries().equals(expected),"actual patched palette keeps exact ID order");
        var level=server.overworld();
        var origin=new BlockPos(96,64,96);var chunk=level.getChunkAt(origin);
        level.setBlock(origin,Blocks.GRANITE.defaultBlockState(),3);level.setBlock(origin.above(),Blocks.DIORITE.defaultBlockState(),3);
        var encoded=ChunkSerializer.write(level,chunk);
        var decoded=ChunkSerializer.read(level,level.getPoiManager(),chunk.getPos(),encoded);
        var random=new Random(731918);
        for(int trial=0;trial<1024;trial++) {
            var pos=new BlockPos(chunk.getPos().getMinBlockX()+random.nextInt(16),level.getMinBuildHeight()+random.nextInt(level.getHeight()),chunk.getPos().getMinBlockZ()+random.nextInt(16));
            check(decoded.getBlockState(pos).equals(chunk.getBlockState(pos)),"native chunk NBT round trip "+pos);
        }
        check(decoded.getBlockState(origin).is(Blocks.GRANITE)&&decoded.getBlockState(origin.above()).is(Blocks.DIORITE),"placed states survive real chunk serialization");
        int missing=0;
        for(String id:new String[]{"dragnlivestock:o_cow","dragnlivestock:o_sheep","dragnlivestock:o_pig","dragnlivestock:o_horse","dragnlivestock:o_donkey","dragnlivestock:o_mule","dragnlivestock:caribou"}) {
            var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id));if(type==null)continue;
            var entity=type.create(level);
            if(entity instanceof AbstractHorse horse&&!horse.getAttributes().hasAttribute(Attributes.JUMP_STRENGTH)) {
                check(horse.getCustomJump()==Attributes.JUMP_STRENGTH.getDefaultValue(),"missing jump attribute safe "+id);
                var bukkit=entity.getClass().getMethod("getBukkitEntity").invoke(entity);
                double strength=((Number)bukkit.getClass().getMethod("getJumpStrength").invoke(bukkit)).doubleValue();
                check(strength==Attributes.JUMP_STRENGTH.getDefaultValue(),"Bukkit/CoreProtect getter safe "+id);missing++;
            }
        }
        check(missing>0,"reported modded mount attribute case reproduced");
        var horse=EntityType.HORSE.create(level);horse.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(1.2);
        check(horse.getCustomJump()==1.2,"existing jump attribute value preserved");
        System.out.println("RUNTIME_NATIVE_DETAILS missing_jump_mounts="+missing+" chunk_roundtrip_positions=1026");
    }
}
