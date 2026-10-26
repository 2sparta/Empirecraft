package ua.empirecraft.ores.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.ores.OreRetrogen;
import java.util.*;

/** Isolated destructive fixture. NEVER install on a real server. */
@Mod("empirecraft_ores_test")
public final class OreSmokeMod {
    MinecraftServer server;ServerLevel level;int tick,checks;
    final String phase=System.getProperty("empirecraft.ores.phase","baseline");
    Block sulfur,zinc,deep;
    final int[] xs={11,12,13};final int z=12;
    public OreSmokeMod(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
    void started(ServerStartedEvent e){server=e.getServer();level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation("minecraft:world_europe")));if(level==null)throw new AssertionError("test dimension missing");sulfur=block("sulfur_gunpowder:sulfurore");zinc=block("create:zinc_ore");deep=block("create:deepslate_zinc_ore");}
    Block block(String id){Block b=ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));check(b!=null && b!=Blocks.AIR,id+" registered");return b;}
    int[] count(LevelChunk c){int[] totals=new int[3];for(int x=0;x<16;x++)for(int zz=0;zz<16;zz++)for(int y=-64;y<=64;y++){Block b=c.getBlockState(new BlockPos(c.getPos().getMinBlockX()+x,y,c.getPos().getMinBlockZ()+zz)).getBlock();if(b==sulfur)totals[0]++;if(b==zinc||b==deep)totals[1]++;if(b==deep)totals[2]++;}return totals;}
    void prepare(){
        for(int cx:xs){LevelChunk c=level.getChunk(cx,z);if(cx==13){for(int x=0;x<16;x++)for(int zz=0;zz<16;zz++)for(int y=-64;y<=64;y++)c.setBlockState(new BlockPos(cx*16+x,y,z*16+zz),Blocks.AIR.defaultBlockState(),false);c.setUnsaved(true);}}
        LevelChunk normal=level.getChunk(11,z);
        for(int x=0;x<16;x++)for(int zz=0;zz<16;zz++)for(int y=-63;y<0;y++)normal.setBlockState(new BlockPos(11*16+x,y,z*16+zz),Blocks.DEEPSLATE.defaultBlockState(),false);
        normal.setUnsaved(true);
        level.setBlock(new BlockPos(11*16,-20,z*16),Blocks.CHEST.defaultBlockState(),2);
        level.setBlock(new BlockPos(11*16+1,-20,z*16),Blocks.DIAMOND_ORE.defaultBlockState(),2);
        for(int x=0;x<5;x++)level.setBlock(new BlockPos(12*16+x,-20,z*16),sulfur.defaultBlockState(),2);
        for(int x=0;x<9;x++)level.setBlock(new BlockPos(12*16+x,-20,z*16+1),zinc.defaultBlockState(),2);
        check(Arrays.equals(count(normal),new int[]{0,0,0}),"old normal chunk has no target ores");
    }
    void verify(){
        LevelChunk normal=level.getChunk(11,z),existing=level.getChunk(12,z),empty=level.getChunk(13,z);
        int[] a=count(normal),b=count(existing),c=count(empty);
        System.out.println("ORE_COUNTS phase="+phase+" normal="+Arrays.toString(a)+" existing="+Arrays.toString(b)+" empty="+Arrays.toString(c));
        check(level.getBlockState(new BlockPos(11*16,-20,z*16)).is(Blocks.CHEST),"chest preserved");
        check(level.getBlockState(new BlockPos(11*16+1,-20,z*16)).is(Blocks.DIAMOND_ORE),"unrelated ore preserved");
        if(phase.equals("retro")){
            check(a[0]==30&&a[1]==30&&a[2]>0,"30 sulfur and 30 zinc including deepslate");check(b[0]==30&&b[1]==30,"existing ores topped up only");
            check(!OreRetrogen.process(level,normal),"completed chunk is not processed twice");
            var caps=normal.writeCapsToNBT();check(caps.getCompound("empirecraft_ore_retrogen:one_time_ores").getBoolean("Complete"),"marker serialized in ForgeCaps");
            for(int cx:xs){LevelChunk chunk=level.getChunk(cx,z);for(int x=0;x<16;x++)for(int zz=0;zz<16;zz++)for(int y=-64;y<=64;y++){BlockPos p=new BlockPos(cx*16+x,y,z*16+zz);Block type=chunk.getBlockState(p).getBlock();if(type==sulfur||type==zinc||type==deep)chunk.setBlockState(p,Blocks.STONE.defaultBlockState(),false);}chunk.setUnsaved(true);}
            level.setBlock(new BlockPos(13*16,0,z*16),Blocks.STONE.defaultBlockState(),2);
        }else{
            check(a[0]==0&&a[1]==0&&b[0]==0&&b[1]==0,"mined ores do not regenerate after restart");
            check(!OreRetrogen.process(level,normal),"completion persisted after restart");
        }
        check(c[0]==0&&c[1]==0,"void chunk unchanged");
        check(empty.writeCapsToNBT().getCompound("empirecraft_ore_retrogen:one_time_ores").getBoolean("Complete"),"void chunk also marked once");
        ServerLevel nether=server.getLevel(net.minecraft.world.level.Level.NETHER);
        check(!OreRetrogen.process(nether,nether.getChunk(0,0)),"disallowed dimension untouched");
    }
    void tick(TickEvent.ServerTickEvent e){if(server==null||e.phase!=TickEvent.Phase.END)return;try{
        tick++;
        if(tick==5){for(int cx:xs)level.setChunkForced(cx,z,true);if(phase.equals("baseline"))prepare();}
        if(tick==100){if(!phase.equals("baseline"))verify();System.out.println("ORE_SMOKE_PASS phase="+phase+" assertions="+checks);server.halt(false);server=null;}
    }catch(Throwable ex){System.out.println("ORE_SMOKE_FAIL phase="+phase+" "+ex);ex.printStackTrace();server.halt(false);server=null;}}
}
