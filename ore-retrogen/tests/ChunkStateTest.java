package ua.empirecraft.ores;

import java.io.File;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Actual Forge capability serializer, including a version-1 save and a disk round trip. */
public final class ChunkStateTest {
    static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        OreRetrogen.ChunkState state = new OreRetrogen.ChunkState();
        CompoundTag legacy = new CompoundTag();
        legacy.putBoolean("Complete",true); legacy.putInt("Version",1);
        legacy.putInt("SulfurAdded",30); legacy.putInt("ZincAdded",27);
        state.deserializeNBT(legacy);
        CompoundTag saved = state.serializeNBT();
        check(saved.getBoolean("Complete"),"old ore completion preserved");
        check(!saved.getBoolean("QuartzComplete"),"legacy chunks still need quartz");
        check(saved.getInt("SulfurAdded")==30 && saved.getInt("ZincAdded")==27,"legacy counters preserved");
        saved.putBoolean("QuartzComplete",true); saved.putInt("QuartzAdded",15);
        state.deserializeNBT(saved);
        File file = new File(args[0]);
        NbtIo.writeCompressed(state.serializeNBT(),file);
        OreRetrogen.ChunkState restarted = new OreRetrogen.ChunkState();
        restarted.deserializeNBT(NbtIo.readCompressed(file));
        CompoundTag restored = restarted.serializeNBT();
        check(restored.getBoolean("Complete") && restored.getBoolean("QuartzComplete"),"both completion flags survive disk reload");
        check(restored.getInt("QuartzAdded")==15,"quartz count survives disk reload");
        saved.putInt("QuartzAdded",0); // Empty/void chunks also finish their one-time pass.
        restarted.deserializeNBT(saved);
        check(restarted.serializeNBT().getBoolean("QuartzComplete"),"empty pass remains completed");
        System.out.println("CHUNK_STATE_TEST_PASS legacy migration, independent flags, NBT disk persistence");
    }
}
