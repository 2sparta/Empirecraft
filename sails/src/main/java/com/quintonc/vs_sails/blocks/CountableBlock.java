package com.quintonc.vs_sails.blocks;

import com.quintonc.vs_sails.ship.SailsShipControl;
import kotlin.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.VsCoreApi;
import org.valkyrienskies.core.api.event.EventConsumer;
import org.valkyrienskies.core.api.event.RegisteredListener;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.impl.program.VSCoreInternal;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.api.VsApi;
import org.valkyrienskies.mod.common.IShipObjectWorldProvider;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.assembly.ICopyableBlock;
import org.valkyrienskies.mod.common.entity.handling.VSEntityHandler;

import java.util.List;
import java.util.Map;

public abstract class CountableBlock extends Block implements ICopyableBlock {
    public CountableBlock(Properties settings) {
        super(settings);
    }

    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClientSide && !state.equals(oldState)) {
            SailsShipControl.markDirty(world, pos);
        }
    }

    @SuppressWarnings({"deprecation","UnstableApiUsage"})
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {

        if (!player.getItemInHand(InteractionHand.MAIN_HAND).is(ItemStack.EMPTY.getItem()) || !VSGameUtilsKt.isBlockInShipyard(world, pos)) {
            return  InteractionResult.PASS;
        }
        if (!world.isClientSide) {
            LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos((ServerLevel) world, pos);
            if (ship == null) return InteractionResult.PASS;
            SailsShipControl controller = SailsShipControl.getOrCreate(ship, world);
            if (controller != null) sendMessage(player, controller);
        }

        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    abstract void addToShip(SailsShipControl controller);

    abstract void removeFromShip(SailsShipControl controller);

    abstract void sendMessage(Player player, SailsShipControl controller);

    @Override
    public @Nullable CompoundTag onCopy(@NotNull ServerLevel serverLevel, @NotNull BlockPos blockPos, @NotNull BlockState blockState, @Nullable BlockEntity blockEntity, @NotNull List<? extends ServerShip> list, @NotNull Map<Long, ? extends org.joml.Vector3dc> map) {
        return null;
    }

    @Override
    public @Nullable CompoundTag onPaste(@NotNull ServerLevel serverLevel, @NotNull BlockPos blockPos, @NotNull BlockState blockState, @NotNull Map<Long, Long> map, @NotNull Map<Long, ? extends Pair<? extends org.joml.Vector3dc, ? extends org.joml.Vector3dc>> map1, @Nullable CompoundTag compoundTag) {
        SailsShipControl.markDirty(serverLevel, blockPos);
        // The global ship-load handler starts a recount after assembly/template loading.
        return null;
    }

    @SuppressWarnings({"deprecation","UnstableApiUsage"})
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClientSide && !state.equals(newState)) {
            SailsShipControl.markDirty(world, pos);
        }
        super.onRemove(state, world, pos, newState, moved);
    }

    @SuppressWarnings("deprecation")
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
