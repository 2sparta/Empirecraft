package ua.empirecraft.trmt;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.Gson;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.TRMTEffects;
import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

/** Only loaded chunks are visited. Existing vanilla TRMT roads retain their original implementation. */
@Mod.EventBusSubscriber(modid="trmt", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class RegionalTrails {
    public record Surface(String source, String kind) {}
    public static final Map<Block, RememberedPath> PATHS = new IdentityHashMap<>();
    private static final Map<LivingEntity, BlockPos> LAST = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ResourceLocation KEY = new ResourceLocation("trmt", "soil_memory");
    public static ResourceLocation pathId(String source) {
        return new ResourceLocation("trmt", "remembered/" + source.replace(':', '/'));
    }
    @Mod.EventBusSubscriber(modid="trmt", bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent e) { e.register(TrailMemory.class); }
        @SubscribeEvent public static void blocks(RegisterEvent e) {
            if (!e.getRegistryKey().equals(ForgeRegistries.Keys.BLOCKS)) return;
            try (var in = RegionalTrails.class.getResourceAsStream("/data/trmt/remembered_soils.json")) {
                if (in == null) throw new IllegalStateException("Missing soil catalog");
                Surface[] catalog = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Surface[].class);
                e.register(ForgeRegistries.Keys.BLOCKS, helper -> {
                    for (Surface surface : catalog) {
                        ResourceLocation id = new ResourceLocation(surface.source());
                        if (!ForgeRegistries.BLOCKS.containsKey(id)) throw new IllegalStateException("Missing soil " + id);
                        Block original = ForgeRegistries.BLOCKS.getValue(id);
                        var path = new RememberedPath(original, surface.kind());
                        PATHS.put(original, path);
                        helper.register(pathId(surface.source()), path);
                    }
                });
            } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
        }
    }
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<LevelChunk> e) {
        if (e.getObject().getLevel().isClientSide) return;
        var memory = new TrailMemory();
        e.addCapability(KEY, memory);
        e.addListener(memory::invalidate);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { LAST.clear(); }

    public static boolean regional(BlockState state) {
        return state.getBlock() instanceof RememberedPath || PATHS.containsKey(state.getBlock());
    }
    /** Called at the start of TRMT's injected walking handler, not as a cancellation of the entity tick. */
    public static boolean walk(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        boolean player = entity instanceof Player;
        Entity feet = player && entity.getVehicle() != null ? entity.getVehicle() : entity;
        BlockPos feetPos = feet.blockPosition();
        if (!level.hasChunkAt(feetPos)) return false;
        BlockState atFeet = level.getBlockState(feetPos);
        BlockPos pos = atFeet.getBlock() instanceof RememberedPath sand
                && "sand".equals(sand.kind) && atFeet.getValue(RememberedPath.WEAR) > 0
                ? feetPos : feetPos.below();
        boolean handled = regional(level.getBlockState(pos));
        if (!feet.onGround()) { LAST.remove(entity); return handled; }
        BlockPos before = LAST.put(entity, pos.immutable());
        if (pos.equals(before)) return handled;
        if ((!player && (!(entity instanceof Mob mob) || !mob.isLeashed()))
            || player && entity.isCrouching()
            || entity.hasEffect(TRMTEffects.LIGHTNESS_ENTRY)
            || feet instanceof LivingEntity living && living.hasEffect(TRMTEffects.LIGHTNESS_ENTRY)) return handled;
        var multipliers = TRMTConfig.get().erosionMultipliers;
        float amount = multipliers.player * (player ? (feet != entity ? multipliers.mounted : 1) : multipliers.leash);
        Direction facing = entity.getDirection();
        step(level, pos, amount, facing);
        step(level, pos.relative(facing), amount * .2f, facing);
        step(level, pos.relative(facing.getCounterClockWise()), amount * .5f, facing);
        step(level, pos.relative(facing.getClockWise()), amount * .5f, facing);
        return handled;
    }
    public static boolean enabled(String kind, boolean regrow) {
        var config = TRMTConfig.get();
        return switch (kind) {
            case "grass" -> regrow ? config.deErosion.grassEnabled : config.erosion.grassEnabled;
            case "sand" -> regrow ? config.deErosion.sandEnabled : config.erosion.sandEnabled;
            default -> regrow ? config.deErosion.dirtEnabled : config.erosion.dirtEnabled;
        };
    }
    public static Block category(String kind) {
        return switch (kind) {case "grass" -> Blocks.GRASS_BLOCK; case "sand" -> Blocks.SAND; default -> Blocks.DIRT;};
    }
    public static void step(ServerLevel level, BlockPos pos, float amount, Direction facing) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || amount <= 0 || !Float.isFinite(amount)) return;
        BlockState state = chunk.getBlockState(pos);
        RememberedPath path = state.getBlock() instanceof RememberedPath p ? p : PATHS.get(state.getBlock());
        if (path == null || !enabled(path.kind, false)) return;
        BlockState above = level.getBlockState(pos.above());
        if ("sand".equals(path.kind)) {
            if (!above.isAir()) return;
        } else if (!above.isAir() && !above.canBeReplaced() && !above.is(Blocks.SNOW)) return;
        var memory = chunk.getCapability(TrailMemory.CAP).orElse(null);
        if (memory == null) throw new IllegalStateException("Missing TRMT chunk capability");
        var entry = memory.entry(level, pos, state, path);
        entry.last = level.getGameTime();
        entry.walked += amount;
        chunk.setUnsaved(true);
        if (entry.walked < entry.threshold) return;
        int stage = state.getBlock() == path ? state.getValue(RememberedPath.WEAR) : -1;
        if (stage < 4) {
            level.setBlock(pos, path.defaultBlockState().setValue(RememberedPath.WEAR, stage + 1)
                .setValue(RememberedPath.FACING, facing), Block.UPDATE_ALL);
        }
        entry.walked = 0;
        Block thresholdBlock = "sand".equals(path.kind) ? milkucha.trmt.TRMTBlocks.ERODED_SAND.get() : category(path.kind);
        entry.threshold = BlockThresholds.randomThreshold(thresholdBlock);
    }
}
