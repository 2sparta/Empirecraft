package ua.empirecraft.interactions.mixin;

import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.interactions.MaterialBalance;

@Mixin(value = Block.class, remap = false)
public abstract class BlockResistanceMixin {
    @Inject(method = "m_7325_()F", at = @At("RETURN"), cancellable = true)
    private void empirecraft$resistance(CallbackInfoReturnable<Float> result) {
        var material = MaterialBalance.of((Block) (Object) this);
        if (material != null) result.setReturnValue(Math.max(result.getReturnValue(), material.blast()));
    }
}
