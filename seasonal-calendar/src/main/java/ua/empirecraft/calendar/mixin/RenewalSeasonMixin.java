package ua.empirecraft.calendar.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ua.empirecraft.calendar.SeasonalPlants;

@Pseudo
@Mixin(targets = "ua.empirecraft.worldrules.NaturalRenewal", remap = false)
public abstract class RenewalSeasonMixin {
    @Redirect(method = {"plant", "tree"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;m_60710_(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"), require = 2)
    private boolean empirecraft$seasonalRenewal(BlockState state, LevelReader level, BlockPos pos) {
        return state.m_60710_(level, pos) && (!(level instanceof ServerLevel server) || SeasonalPlants.canGrow(server, pos, state));
    }
}
