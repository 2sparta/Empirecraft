package ua.empirecraft.trmt;

import java.util.List;
import milkucha.trmt.erosion.BlockThresholds;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.HitResult;

/** No block entity, ticker or separate item. A road is still its original soil when harvested. */
public final class RememberedPath extends Block {
    public static final IntegerProperty WEAR = IntegerProperty.create("wear", 0, 4);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final Block original;
    public final String kind;
    public RememberedPath(Block original, String kind) {
        super(BlockBehaviour.Properties.copy(original).randomTicks().strength(.6f).noLootTable());
        this.original = original; this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(WEAR, 0).setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(WEAR, FACING); }
    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!RegionalTrails.enabled(kind, true)) return;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return;
        var memory = chunk.getCapability(TrailMemory.CAP).orElse(null);
        if (memory == null) return;
        var e = memory.entry(level, pos, state, this);
        int stage = state.getValue(WEAR);
        long timeout = switch (kind) {
            case "grass" -> BlockThresholds.getGrassDeErosionTimeout(stage + 1);
            case "sand" -> BlockThresholds.getSandDeErosionTimeout(stage);
            default -> BlockThresholds.getDirtDeErosionTimeout((Block)milkucha.trmt.TRMTBlocks.ERODED_DIRT.get());
        };
        if (level.getGameTime() - e.last < Math.max(1L, timeout)) return;
        if (stage > 0) {
            level.setBlock(pos, state.setValue(WEAR, stage - 1), UPDATE_ALL);
            e.last = level.getGameTime(); e.walked = 0;
        } else {
            BlockState origin = memory.restore(level, e, this);
            level.setBlock(pos, origin, UPDATE_ALL);
            memory.remove(pos);
        }
        chunk.setUnsaved(true);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        super.onRemove(state, level, pos, next, moved);
        if (!level.isClientSide && state.getBlock() != next.getBlock()) {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null) chunk.getCapability(TrailMemory.CAP).ifPresent(memory -> { memory.remove(pos); chunk.setUnsaved(true); });
        }
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockState source = original.defaultBlockState();
        return original.getDrops(source, builder.withParameter(LootContextParams.BLOCK_STATE, source));
    }
    @Override public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        return original.getCloneItemStack(original.defaultBlockState(), target, level, pos, player);
    }
}
