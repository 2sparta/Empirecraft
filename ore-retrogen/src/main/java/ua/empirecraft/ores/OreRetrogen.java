package ua.empirecraft.ores;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import java.util.*;

@Mod(OreRetrogen.ID)
public final class OreRetrogen {
    public static final String ID = "empirecraft_ore_retrogen";
    private static final Logger LOG = LogUtils.getLogger();
    private static final Capability<ChunkState> CAP = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation KEY = new ResourceLocation(ID,"one_time_ores");
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;
    private static final ForgeConfigSpec.IntValue SULFUR, ZINC, MIN_Y, MAX_Y, CLEARANCE, INTERVAL, BUDGET;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.define("enabled",true);
        DIMENSIONS = b.comment("Exact dimension IDs. No other worlds are modified.").defineList("dimensions",
            List.of("minecraft:overworld","minecraft:world_europe"), v -> v instanceof String s && ResourceLocation.tryParse(s) != null);
        SULFUR = b.comment("One-time target including ores already present in the height band.").defineInRange("sulfurPerChunk",30,0,256);
        ZINC = b.defineInRange("zincPerChunk",30,0,256);
        MIN_Y = b.defineInRange("minY",-64,-2032,2031);
        MAX_Y = b.defineInRange("maxY",64,-2032,2031);
        CLEARANCE = b.comment("Keep ores this far below the surface or seafloor.").defineInRange("surfaceClearance",8,1,64);
        INTERVAL = b.defineInRange("intervalTicks",5,1,1200);
        BUDGET = b.comment("Global budget across all worlds. No chunks are force-loaded.").defineInRange("chunksPerInterval",1,1,8);
        SPEC = b.build();
    }
    private record Job(ServerLevel level, int x, int z) {}
    private final LinkedHashSet<Job> pending = new LinkedHashSet<>(); // server-thread only; coordinates, not chunk refs
    private int ticks;

    public OreRetrogen() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,SPEC,"empirecraft_ore_retrogen-common.toml");
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterCapabilitiesEvent e) -> e.register(ChunkState.class));
        MinecraftForge.EVENT_BUS.addGenericListener(LevelChunk.class,this::attach);
        MinecraftForge.EVENT_BUS.addListener(this::loaded);
        MinecraftForge.EVENT_BUS.addListener(this::unloaded);
        MinecraftForge.EVENT_BUS.addListener(this::levelUnloaded);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> { pending.clear(); ticks = 0; });
    }
    public static final class ChunkState implements ICapabilitySerializable<CompoundTag> {
        private final LazyOptional<ChunkState> optional = LazyOptional.of(() -> this);
        private boolean complete;
        private int sulfurAdded, zincAdded;
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return capability == CAP ? optional.cast() : LazyOptional.empty();
        }
        @Override public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Complete",complete); tag.putInt("Version",1);
            tag.putInt("SulfurAdded",sulfurAdded); tag.putInt("ZincAdded",zincAdded);
            return tag;
        }
        @Override public void deserializeNBT(CompoundTag tag) {
            complete = tag.getBoolean("Complete");
            sulfurAdded = tag.getInt("SulfurAdded"); zincAdded = tag.getInt("ZincAdded");
        }
        void invalidate() { optional.invalidate(); }
    }
    private void attach(AttachCapabilitiesEvent<LevelChunk> e) {
        // The same ForgeCaps tag is saved atomically with the chunk's block palette.
        if (e.getObject().getLevel().isClientSide) return;
        ChunkState state = new ChunkState(); e.addCapability(KEY,state); e.addListener(state::invalidate);
    }
    private static boolean allowed(ServerLevel level) {
        return ENABLED.get() && DIMENSIONS.get().contains(level.dimension().location().toString());
    }
    private void loaded(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getChunk() instanceof LevelChunk chunk)) return;
        int x = chunk.getPos().x, z = chunk.getPos().z;
        // Load events can arrive outside the server thread. Never inspect blocks or mutate the queue there.
        level.getServer().execute(() -> { if (allowed(level)) pending.add(new Job(level,x,z)); });
    }
    private void unloaded(ChunkEvent.Unload e) {
        if (e.getLevel() instanceof ServerLevel level) {
            int x = e.getChunk().getPos().x, z = e.getChunk().getPos().z;
            level.getServer().execute(() -> pending.remove(new Job(level,x,z)));
        }
    }
    private void levelUnloaded(LevelEvent.Unload e) {
        if (e.getLevel() instanceof ServerLevel level)
            level.getServer().execute(() -> pending.removeIf(j -> j.level == level));
    }
    private void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % INTERVAL.get() != 0 || !ENABLED.get()) return;
        for (int n = 0; n < BUDGET.get() && !pending.isEmpty(); n++) {
            Iterator<Job> iterator = pending.iterator(); Job job = iterator.next(); iterator.remove();
            if (!allowed(job.level)) continue;
            LevelChunk chunk = job.level.getChunkSource().getChunkNow(job.x,job.z);
            if (chunk == null) continue; // Never load or generate a chunk to process a job.
            try { process(job.level,chunk); }
            catch (Exception ex) { LOG.error("[OreRetrogen] Failed in {} {},{}; will retry on next load",job.level.dimension().location(),job.x,job.z,ex); }
        }
    }
    /** Called only on the server thread. Returns false for an already completed chunk. */
    public static boolean process(ServerLevel level,LevelChunk chunk) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Ore generation must run on the server thread");
        if (!allowed(level) || !level.getWorldBorder().isWithinBounds(chunk.getPos())) return false;
        ChunkState state = chunk.getCapability(CAP).orElse(null);
        if (state == null || state.complete) return false;
        Block sulfur = requireBlock("sulfur_gunpowder:sulfurore");
        Block zinc = requireBlock("create:zinc_ore");
        Block deepZinc = requireBlock("create:deepslate_zinc_ore");
        int min = Math.max(MIN_Y.get(),level.getMinBuildHeight());
        int max = Math.min(MAX_Y.get(),level.getMaxBuildHeight()-1);
        if (min > max) throw new IllegalStateException("minY exceeds maxY in this dimension");
        int[] ceiling = new int[256];
        for (int x=0;x<16;x++) for (int z=0;z<16;z++)
            ceiling[z*16+x] = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z)-CLEARANCE.get();
        int originX = chunk.getPos().getMinBlockX(), originZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        OrePass.Terrain terrain = new OrePass.Terrain() {
            public int minY() { return min; }
            public int maxY() { return max; }
            public int ceiling(int x,int z) { return ceiling[z*16+x]; }
            private BlockPos position(OrePass.Pos p) { return cursor.set(originX+p.x(),p.y(),originZ+p.z()); }
            public OrePass.Kind kind(OrePass.Pos p) {
                Block block = chunk.getBlockState(position(p)).getBlock();
                if (block == sulfur) return OrePass.Kind.SULFUR;
                if (block == zinc || block == deepZinc) return OrePass.Kind.ZINC;
                if (block == Blocks.DEEPSLATE || block == Blocks.TUFF) return OrePass.Kind.DEEP;
                if (block == Blocks.STONE || block == Blocks.GRANITE || block == Blocks.DIORITE || block == Blocks.ANDESITE)
                    return OrePass.Kind.STONE;
                return OrePass.Kind.OTHER;
            }
            public boolean place(OrePass.Pos p,OrePass.Kind ore,boolean deep) {
                // A normal block update keeps connected clients and lighting in sync. Every position stays in this chunk.
                BlockState target = (ore == OrePass.Kind.SULFUR ? sulfur : deep ? deepZinc : zinc).defaultBlockState();
                return level.setBlock(position(p),target,Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        };
        long seed = level.getSeed() ^ chunk.getPos().toLong() ^ ((long)level.dimension().location().hashCode()<<32) ^ 0x53554C465552L;
        OrePass.Result result = OrePass.run(terrain,seed,SULFUR.get(),ZINC.get());
        state.sulfurAdded = result.sulfurAdded(); state.zincAdded = result.zincAdded(); state.complete = true;
        chunk.setUnsaved(true); // Also needed for a completed chunk with zero eligible blocks.
        LOG.debug("[OreRetrogen] {} {}: sulfur +{}, zinc +{} (previously {}, {})",level.dimension().location(),chunk.getPos(),
            result.sulfurAdded(),result.zincAdded(),result.sulfurExisting(),result.zincExisting());
        return true;
    }
    private static Block requireBlock(String name) {
        ResourceLocation id = new ResourceLocation(name);
        if (!ForgeRegistries.BLOCKS.containsKey(id)) throw new IllegalStateException("Missing ore block: "+name);
        return Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(id));
    }
    private void commands(RegisterCommandsEvent e) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("empireores").requires(s -> s.hasPermission(2));
        root.then(Commands.literal("status").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal("Ore retrogen enabled="+ENABLED.get()+"; queued="+pending.size()
                +"; sulfur="+SULFUR.get()+"; zinc="+ZINC.get()+"; dimensions="+DIMENSIONS.get()),false); return 1;
        }));
        root.then(Commands.literal("here").executes(ctx -> {
            var player = ctx.getSource().getPlayerOrException();
            var p = player.chunkPosition(); LevelChunk chunk = player.serverLevel().getChunkSource().getChunkNow(p.x,p.z);
            ChunkState state = chunk == null ? null : chunk.getCapability(CAP).orElse(null);
            String message = "Chunk "+p+": complete="+(state != null && state.complete)
                +"; added sulfur="+(state == null ? 0 : state.sulfurAdded)+"; zinc="+(state == null ? 0 : state.zincAdded);
            ctx.getSource().sendSuccess(() -> Component.literal(message),false); return 1;
        }));
        e.getDispatcher().register(root);
    }
}
