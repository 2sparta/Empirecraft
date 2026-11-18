package ua.empirecraft.interactions.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.github.alexthe666.alexsmobs.block.BlockSculkBoomer", remap = false)
public abstract class DisabledSculkMixin {
    @Inject(method = "m_142354_", at = @At("HEAD"), cancellable = true)
    private void empirecraft$disableTick(Level level, BlockState state, BlockEntityType<?> type,
                                        CallbackInfoReturnable<BlockEntityTicker<?>> result) {
        result.setReturnValue(null);
    }

    @Inject(method = "m_214009_", at = @At("HEAD"), cancellable = true)
    private void empirecraft$disableListener(ServerLevel level, BlockEntity entity,
                                            CallbackInfoReturnable<GameEventListener> result) {
        result.setReturnValue(null);
    }
}
