package ua.empirecraft.forging;

import net.minecraft.core.BlockPos;

/** Loadable runtime bridge; Mixin implementation classes must never escape into descriptors. */
public interface CounterPacketPosition {
    BlockPos empirecraft$position();
}
