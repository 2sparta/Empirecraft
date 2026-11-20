package ua.empirecraft.trmt;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.*;
import net.minecraft.core.Direction;
import milkucha.trmt.erosion.BlockThresholds;

/** Forge persists this capability with each chunk; entries never retain a level, chunk or player. */
public final class TrailMemory implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<TrailMemory> CAP = CapabilityManager.get(new CapabilityToken<>() {});
    private final LazyOptional<TrailMemory> optional = LazyOptional.of(() -> this);
    private final Map<Long, Entry> entries = new HashMap<>();
    public static final class Entry {
        public CompoundTag original;
        public float walked, threshold;
        public long last;
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return cap == CAP ? optional.cast() : LazyOptional.empty();
    }
    public void invalidate() { optional.invalidate(); }
    public Entry entry(ServerLevel level, BlockPos pos, BlockState state, RememberedPath path) {
        Entry e = entries.get(pos.asLong());
        if (e != null && !(state.getBlock() instanceof RememberedPath)
                && !e.original.equals(NbtUtils.writeBlockState(state))) {
            entries.remove(pos.asLong()); e = null; // replaced terrain must start with its own origin
        }
        if (e == null) {
            e = new Entry();
            e.original = NbtUtils.writeBlockState(state.getBlock() == path ? path.original.defaultBlockState() : state);
            e.threshold = BlockThresholds.randomThreshold(RegionalTrails.category(path.kind));
            e.last = level.getGameTime();
            entries.put(pos.asLong(), e);
        }
        return e;
    }
    public void remove(BlockPos pos) { entries.remove(pos.asLong()); }
    public BlockState restore(ServerLevel level, Entry entry, RememberedPath path) {
        BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), entry.original);
        return state.getBlock() == path.original ? state : path.original.defaultBlockState();
    }
    @Override public CompoundTag serializeNBT() {
        CompoundTag out = new CompoundTag();
        entries.forEach((pos, entry) -> {
            CompoundTag tag = new CompoundTag();
            tag.put("original", entry.original.copy()); tag.putFloat("walked", entry.walked);
            tag.putFloat("threshold", entry.threshold); tag.putLong("last", entry.last);
            out.put(Long.toString(pos), tag);
        });
        return out;
    }
    @Override public void deserializeNBT(CompoundTag data) {
        entries.clear();
        for (String key : data.getAllKeys()) {
            try {
                long pos = Long.parseLong(key);
                CompoundTag tag = data.getCompound(key);
                if (!tag.contains("original", Tag.TAG_COMPOUND)) continue;
                var e = new Entry(); e.original = tag.getCompound("original").copy();
                e.walked = tag.getFloat("walked"); e.threshold = tag.getFloat("threshold"); e.last = tag.getLong("last");
                if (!Float.isFinite(e.walked) || !Float.isFinite(e.threshold) || e.threshold <= 0) continue;
                entries.put(pos, e);
            } catch (NumberFormatException ignored) { }
        }
    }
}
