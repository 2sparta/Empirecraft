package ua.empirecraft.lockscompat.mixin;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CompoundContainer.class, remap = false)
public interface CompoundContainerAccess {
    @Accessor("f_18910_")
    Container empirecraftLocks$getFirst();

    @Accessor("f_18911_")
    Container empirecraftLocks$getSecond();
}
