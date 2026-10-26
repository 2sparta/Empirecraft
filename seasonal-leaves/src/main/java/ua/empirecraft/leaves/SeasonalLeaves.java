package ua.empirecraft.leaves;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import sereneseasons.api.season.SeasonHelper;

/** All world access stays on the server thread. No extra blocks or client packets. */
@Mod(SeasonalLeaves.ID)
public final class SeasonalLeaves {
    public static final String ID = "empirecraft_seasonal_leaves";
    private static final String DATA = ID + "_v1";
    private static final TagKey<Block> EXTRA_LEAVES = tag("additional_leaves");
    private static final TagKey<Block> EXTRA_LOGS = tag("additional_logs");
    private static final TagKey<Block> EVERGREEN = tag("evergreen");
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue SCAN, ATTEMPTS, INTERVAL, MAX_RECORDS;
    private static final ForgeConfigSpec.BooleanValue ENABLED, PERSISTENT;
    static {
        var b = new ForgeConfigSpec.Builder();
        ENABLED = b.define("enabled", true);
        PERSISTENT = b.comment("Include persistent leaves (WorldPainter maps may need this). False protects player decorations.").define("includePersistentLeaves", false);
        SCAN = b.defineInRange("positionsPerLevelTick", 4096, 64, 65536);
        ATTEMPTS = b.defineInRange("leafAttemptsPerLevelTick", 8, 1, 128);
        INTERVAL = b.defineInRange("rescanDelayTicks", 1200, 20, 72000);
        MAX_RECORDS = b.comment("Per-chunk backup safety cap; excess leaves stay intact.").defineInRange("maxSavedLeavesPerChunk", 32768, 256, 262144);
        SPEC = b.build();
    }
    private final Map<ChunkAccess, CompoundTag> backups = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<ServerLevel, LinkedHashMap<Long, Work>> worlds = new IdentityHashMap<>();
    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, new ResourceLocation(ID, name));
    }
    public SeasonalLeaves() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
        MinecraftForge.EVENT_BUS.addListener(this::read);
        MinecraftForge.EVENT_BUS.addListener(this::save);
        MinecraftForge.EVENT_BUS.addListener(this::load);
        MinecraftForge.EVENT_BUS.addListener(this::unload);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::placed);
        MinecraftForge.EVENT_BUS.addListener(this::broken);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
    }
    private void stopped(ServerStoppedEvent event) { worlds.clear(); backups.clear(); }
    private void read(ChunkDataEvent.Load event) {
        // This event may run on the chunk I/O thread: only copy NBT, never inspect the world.
        backups.put(event.getChunk(), event.getData().getCompound(DATA).copy());
    }
    private void save(ChunkDataEvent.Save event) {
        CompoundTag records = backups.get(event.getChunk());
        if (records != null) event.getData().put(DATA, records.copy());
    }
    private void load(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            worlds.computeIfAbsent(level, x -> new LinkedHashMap<>()).put(chunk.getPos().toLong(), new Work(chunk, backups.computeIfAbsent(chunk, x -> new CompoundTag())));
    }
    private void unload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var map = worlds.get(level);
            if (map != null) map.remove(event.getChunk().getPos().toLong());
        }
    }
    // Any player replacement cancels the dormant leaf, even if that block is later removed.
    private void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) cancel(level, event.getPos());
    }
    private void broken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) cancel(level, event.getPos());
    }
    private void cancel(ServerLevel level, BlockPos pos) {
        Work w = work(level, pos);
        if (w != null && w.records.contains(key(pos))) { w.records.remove(key(pos)); w.dirty(); }
    }
    private Work work(ServerLevel level, BlockPos pos) {
        var map = worlds.get(level);
        return map == null ? null : map.get(net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    }
    private static String key(BlockPos pos) { return Long.toString(pos.asLong()); }
    private static boolean leaves(BlockState state) { return state.is(BlockTags.LEAVES) || state.is(EXTRA_LEAVES); }
    private static boolean log(BlockState state) { return state.is(BlockTags.LOGS) || state.is(EXTRA_LOGS); }
    private static boolean eligible(BlockState state) {
        return leaves(state) && !state.is(EVERGREEN) && !state.hasBlockEntity()
            && (!state.hasProperty(BlockStateProperties.WATERLOGGED) || !state.getValue(BlockStateProperties.WATERLOGGED))
            && (PERSISTENT.get() || !state.hasProperty(BlockStateProperties.PERSISTENT) || !state.getValue(BlockStateProperties.PERSISTENT));
    }
    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
    private static boolean anchorValid(ServerLevel level, CompoundTag record) {
        BlockPos anchor = BlockPos.of(record.getLong("anchor"));
        return loaded(level, anchor) && log(level.getBlockState(anchor))
            && BuiltInRegistries.BLOCK.getKey(level.getBlockState(anchor).getBlock()).toString().equals(record.getString("wood"));
    }
    /** Follow the crown, including already dormant leaves. Never load adjacent chunks. */
    private BlockPos anchor(ServerLevel level, BlockPos start) {
        var queue = new ArrayDeque<BlockPos>();
        var depths = new ArrayDeque<Integer>();
        var visited = new HashSet<Long>();
        queue.add(start); depths.add(0); visited.add(start.asLong());
        while (!queue.isEmpty() && visited.size() <= 512) {
            BlockPos pos = queue.remove(); int depth = depths.remove();
            if (!loaded(level, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (log(state)) return pos;
            Work w = work(level, pos);
            boolean dormant = w != null && w.records.contains(key(pos), Tag.TAG_COMPOUND);
            if (dormant) {
                var record = w.records.getCompound(key(pos));
                if (anchorValid(level, record)) return BlockPos.of(record.getLong("anchor"));
            }
            if (depth >= 12 || (!pos.equals(start) && !leaves(state) && !dormant)) continue;
            for (Direction d : Direction.values()) {
                BlockPos next = pos.relative(d);
                if (visited.add(next.asLong())) { queue.add(next); depths.add(depth + 1); }
            }
        }
        return null;
    }
    private void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var map = worlds.get(level);
        if (map == null || map.isEmpty()) return;
        boolean seasonal = sereneseasons.init.ModConfig.seasons.isDimensionWhitelisted(level.dimension());
        var season = SeasonHelper.getSeasonState(level);
        double fraction = ENABLED.get() && seasonal ? SeasonCurve.bareFraction(season.getSeasonCycleTicks(), season.getSubSeasonDuration()) : 0;
        int scans = SCAN.get(), attempts = ATTEMPTS.get();
        // Rotate the insertion-ordered queue for fairness between distant loaded chunks.
        int visits = map.size();
        while (visits-- > 0 && scans > 0 && attempts > 0) {
            var iterator = map.entrySet().iterator(); var entry = iterator.next();
            Work w = entry.getValue(); iterator.remove(); map.put(entry.getKey(), w);
            if (w.chunk.getLevel() != level || level.getChunkSource().getChunkNow(w.chunk.getPos().x, w.chunk.getPos().z) != w.chunk) continue;
            if (w.lastFraction != fraction) { w.lastFraction = fraction; w.nextScan = 0; }
            // Restore backups before scanning live leaves. Key snapshot rebuilt once per pass.
            if (w.restore == null) w.restore = new ArrayDeque<>(w.records.getAllKeys());
            while (!w.restore.isEmpty() && attempts > 0 && scans > 0) {
                String key = w.restore.remove(); scans--;
                if (!w.records.contains(key, Tag.TAG_COMPOUND)) continue;
                CompoundTag record = w.records.getCompound(key); BlockPos pos = BlockPos.of(record.getLong("pos"));
                if (SeasonCurve.bare(pos.asLong(), fraction)) continue;
                attempts--;
                ResourceLocation originalId = ResourceLocation.tryParse(record.getCompound("state").getString("Name"));
                if (originalId == null || !BuiltInRegistries.BLOCK.containsKey(originalId)) continue;
                BlockPos support = BlockPos.of(record.getLong("anchor"));
                if (!loaded(level, support)) continue; // Retry after the neighbouring chunk loads.
                BlockState current = level.getBlockState(pos);
                if (current.isAir() || current.is(Blocks.SNOW)) {
                    if (anchorValid(level, record)) {
                        BlockState original = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), record.getCompound("state"));
                        // Missing mod content must remain recoverable, never deserialize it into air.
                        if (original.isAir()) continue;
                        level.setBlock(pos, original, Block.UPDATE_CLIENTS);
                    }
                }
                w.records.remove(key); w.dirty();
            }
            if (!w.restore.isEmpty()) continue;
            if (level.getGameTime() < w.nextScan) continue;
            if (!level.getWorldBorder().isWithinBounds(w.chunk.getPos().getWorldPosition())) {
                w.nextScan = level.getGameTime() + INTERVAL.get(); continue;
            }
            var sections = w.chunk.getSections();
            while (w.cursor < sections.length * 4096 && scans > 0 && attempts > 0) {
                int sectionIndex = w.cursor >>> 12;
                var section = sections[sectionIndex];
                if (section.hasOnlyAir() || !section.maybeHas(SeasonalLeaves::eligible)) {
                    w.cursor = (sectionIndex + 1) * 4096; scans--; continue;
                }
                int local = w.cursor++ & 4095; scans--;
                BlockState state = section.getBlockState(local & 15, local >>> 8, (local >>> 4) & 15);
                if (!eligible(state) || fraction <= 0) continue;
                BlockPos pos = new BlockPos(w.chunk.getPos().getMinBlockX() + (local & 15),
                    w.chunk.getMinBuildHeight() + sectionIndex * 16 + (local >>> 8),
                    w.chunk.getPos().getMinBlockZ() + ((local >>> 4) & 15));
                if (!SeasonCurve.bare(pos.asLong(), fraction) || w.records.size() >= MAX_RECORDS.get()) continue;
                attempts--;
                if (SeasonHelper.usesTropicalSeasons(level.getBiome(pos))) continue;
                BlockPos support = anchor(level, pos);
                if (support == null) continue;
                CompoundTag record = new CompoundTag();
                record.putLong("pos", pos.asLong()); record.put("state", NbtUtils.writeBlockState(state));
                record.putLong("anchor", support.asLong());
                record.putString("wood", BuiltInRegistries.BLOCK.getKey(level.getBlockState(support).getBlock()).toString());
                w.records.put(key(pos), record); w.dirty(); // Backup is attached before the block changes.
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            if (w.cursor >= sections.length * 4096) {
                w.cursor = 0; w.nextScan = level.getGameTime() + INTERVAL.get(); w.restore = null;
            }
        }
    }
    private static final class Work {
        final LevelChunk chunk;
        final CompoundTag records;
        ArrayDeque<String> restore;
        int cursor;
        long nextScan;
        double lastFraction = -1;
        Work(LevelChunk chunk, CompoundTag records) {
            this.chunk = chunk;
            this.records = records;
        }
        void dirty() { chunk.setUnsaved(true); }
    }
}
