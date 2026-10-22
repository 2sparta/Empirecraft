package ua.empirecraft.lockscompat;

import melonslise.locks.common.util.LocksUtil;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ua.empirecraft.lockscompat.mixin.CompoundContainerAccess;

public final class LockedInventory {
    private LockedInventory() {}

    public static boolean isLocked(Container inventory) {
        if (inventory instanceof BlockEntity blockEntity) {
            Level level = blockEntity.m_58904_();
            return level != null && !level.f_46443_
                && LocksUtil.locked(level, blockEntity.m_58899_());
        }
        if (inventory instanceof CompoundContainer) {
            CompoundContainerAccess parts = (CompoundContainerAccess) inventory;
            return isLocked(parts.empirecraftLocks$getFirst())
                || isLocked(parts.empirecraftLocks$getSecond());
        }
        return false;
    }
}
