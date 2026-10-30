package ua.empirecraft.forging.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.stirdrem.overgeared.util.BrokenHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.forging.*;

/** Compute once from the item's native speed, before vanilla applies environmental effects.
 * The HEAD return bypasses Overgeared's RETURN multiplier, preventing double bonuses.
 * Other mods' Forge BreakSpeed events still run in the normal Player pipeline.
 */
@Mixin(value=ItemStack.class,priority=2000,remap=false)
public abstract class QualityMiningMixin {
    @Inject(method="m_41691_(Lnet/minecraft/world/level/block/state/BlockState;)F",at=@At("HEAD"),cancellable=true,require=1)
    private void empirecraft$toolSpeed(BlockState state,CallbackInfoReturnable<Float> cir){
        ItemStack stack=(ItemStack)(Object)this;
        if(!ToolFeedback.tool(stack))return;
        if(BrokenHelper.isBroken(stack)){cir.setReturnValue(0.0f);return;}
        String quality=EquipmentQuality.quality(stack);if(quality.isEmpty())return;
        float base=stack.m_41720_().m_8102_(stack,state);
        cir.setReturnValue(MiningRules.speed(quality,base));
    }
}
