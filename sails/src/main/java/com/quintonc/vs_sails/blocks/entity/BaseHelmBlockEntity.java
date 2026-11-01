package com.quintonc.vs_sails.blocks.entity;

import com.quintonc.vs_sails.networking.PacketHandler;
import com.quintonc.vs_sails.registration.SailsBlocks;
import com.quintonc.vs_sails.registration.SailsItems;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ContainerSingleItem;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.entity.ShipMountingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.quintonc.vs_sails.blocks.HelmBlock.FACING;
import static java.lang.Math.sqrt;

public abstract class BaseHelmBlockEntity extends BlockEntity implements Clearable, ContainerSingleItem {

    public static final Logger LOGGER = LoggerFactory.getLogger("base_helm_entity");

    public static int wheelInterval;
    private List<ShipMountingEntity> seats = new ArrayList<ShipMountingEntity>();

    public int wheelAngle;
    public float renderWheelAngle = 360f;
    public float renderWheelAngleVel = 0;
    public static int maxAngle;

    private final NonNullList<ItemStack> items;

    public BaseHelmBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
    }

    public boolean startRiding(Player player, boolean force, BlockPos pos, BlockState state, ServerLevel world) {
        if (!com.quintonc.vs_sails.protection.ShipProtection.allows(player,world,pos)) return false;

        for (int i = seats.size()-1; i >= 0; i--) {
            if (!seats.get(i).isVehicle()) {
                seats.get(i).kill();
                seats.remove(i);
            } else if (!seats.get(i).isAlive()) {
                seats.remove(i);
            }
        }

        if (!seats.isEmpty()) return false;
        ShipMountingEntity seat = spawnSeat(pos, state, world);
        if (seat == null) return false;
        boolean ride = player.startRiding(seat, force);

        if (ride) {
            //controller. = player
            seats.add(seat);
        } else {
            seat.discard();
        }

        return ride;
    }

    ShipMountingEntity spawnSeat(BlockPos pos, BlockState state, ServerLevel world) {
        BlockPos newPos = pos.relative(state.getValue(FACING).getOpposite());

        Vector3dc mounterPos;
        if (state.getValue(FACING) == Direction.NORTH) {
            mounterPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.125, pos.getZ() + 1.3125);
        } else if (state.getValue(FACING) == Direction.SOUTH) {
            mounterPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.125, pos.getZ() - 0.3125);
        } else if (state.getValue(FACING) == Direction.EAST) {
            mounterPos = new Vector3d(pos.getX() - 0.3125, pos.getY() + 0.125, pos.getZ() + .5);
        } else {
            mounterPos = new Vector3d(pos.getX() + 1.3125, pos.getY() + 0.125, pos.getZ() + .5);
        }

        ShipMountingEntity entity = ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE.create(world);

        if (entity == null) return null;
        entity.setPos(mounterPos.x(), mounterPos.y(), mounterPos.z());
        entity.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(pos.getX(),pos.getY(),pos.getZ()));
        entity.move(MoverType.SELF, new Vec3(0, 0, 0));
        entity.setController(true);
        world.addFreshEntityWithPassengers(entity);
        return entity;
    }

    public boolean sit(Player player) {
        boolean force = false;
//        if (!force && player.getVehicle().getType() == ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE && seats.contains((ShipMountingEntity) player.getVehicle())) {
//
//        }

        return startRiding(player, force, this.getBlockPos(), this.getBlockState(), (ServerLevel) level);
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            for (int i = seats.size()-1; i >= 0; i--) {
                seats.get(i).kill();
            }
            seats.clear();
        }

        super.setRemoved();
    }

    public boolean rotateWheelRight(BlockState state, ServerLevel world, BlockPos pos) {
        return rotateWheel(world, pos, -wheelInterval);
    }

    public boolean rotateWheelLeft(BlockState state, ServerLevel world, BlockPos pos) {
        return rotateWheel(world, pos, wheelInterval);
    }

    private boolean rotateWheel(ServerLevel world, BlockPos pos, int step) {
        int next = Math.max(0, Math.min(720, wheelAngle + step));
        if (next == wheelAngle || getFirstItem().isEmpty()) return false;
        wheelAngle = next;
        playWheelSounds(world, pos);
        setChanged();
        return true;
    }

    public boolean hasPilot() {
        if (level instanceof ServerLevel server) for (var seat:seats) for (var passenger:List.copyOf(seat.getPassengers()))
            if (passenger instanceof Player p && !com.quintonc.vs_sails.protection.ShipProtection.allows(p,server,getBlockPos()))p.stopRiding();
        return seats.stream().anyMatch(seat -> seat.isAlive() && seat.isVehicle());
    }

    private void playWheelSounds(Level world, BlockPos pos) {
        if ((double)wheelAngle/ BaseHelmBlockEntity.maxAngle == 0.5) {
            world.playSound(null, pos.below(), SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                    SoundSource.BLOCKS, 1.5f, world.getRandom().nextFloat() * 0.1F + 0.9F);
            world.playSound(null, pos.below(), SoundEvents.ARMOR_EQUIP_CHAIN,
                    SoundSource.BLOCKS, 0.1f, world.getRandom().nextFloat() * 0.1F + 0.9F);
        } else if (wheelAngle == BaseHelmBlockEntity.maxAngle || wheelAngle == 0) {
            world.playSound(null, pos.below(), SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                    SoundSource.BLOCKS, 1.5f, world.getRandom().nextFloat() * 0.1F + 0.9F);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        super.saveAdditional(pTag);
        if (!this.getFirstItem().isEmpty()) {
            pTag.put("wheel_item", this.getFirstItem().save(new CompoundTag()));
        }
        pTag.putInt("wheel_angle", wheelAngle);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        if (pTag.contains("wheel_item", 10)) {
            this.items.set(0, ItemStack.of(pTag.getCompound("wheel_item")));
        } else {
            this.items.set(0, ItemStack.EMPTY);
        }
        if (this.items.get(0).getCount() > 1) this.items.get(0).setCount(1);
        wheelAngle = pTag.contains("wheel_angle", 3) ? Math.max(0, Math.min(720, pTag.getInt("wheel_angle"))) : 360;
        renderWheelAngle = wheelAngle;
    }

    public int getWheelAngle() {
        return wheelAngle;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? items.get(0) : ItemStack.EMPTY;
    }

    @Override
    public int getMaxStackSize() { return 1; }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0) return ItemStack.EMPTY;
        ItemStack removed = items.get(0).split(amount);
        if (items.get(0).isEmpty()) items.set(0, ItemStack.EMPTY);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack removed = items.set(0, ItemStack.EMPTY);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public void clearContent() {
        items.set(0, ItemStack.EMPTY);
        setChanged();
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation("helm_wheels"));
        if (!stack.isEmpty() && !stack.is(tag)) return;
        items.set(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    public boolean canPlaceItem(int index, ItemStack stack) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation("helm_wheels"));
        return index == 0 && stack.is(tag) && this.getItem(index).isEmpty();
    }

    public void dropWheel() {
        if (this.level != null && !this.level.isClientSide) {
            BlockPos blockPos = this.getBlockPos();
            ItemStack itemStack = this.getFirstItem();
            if (!itemStack.isEmpty()) {
                this.removeFirstItem();
                Vec3 vec3 = Vec3.atLowerCornerWithOffset(blockPos, 0.5, 1.01, 0.5).offsetRandom(this.level.random, 0.7F);
                ItemStack itemStack2 = itemStack.copy();
                ItemEntity itemEntity = new ItemEntity(this.level, vec3.x(), vec3.y(), vec3.z(), itemStack2);
                itemEntity.setDefaultPickUpDelay();
                this.level.addFreshEntity(itemEntity);
            }
        }
    }

}
