package ua.empirecraft.interactions.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.interactions.FireRules;

@Mixin(targets = {"net.minecraft.world.item.FlintAndSteelItem", "net.minecraft.world.item.FireChargeItem"}, remap = false)
public abstract class IgniterMixin {
    @Inject(method = "m_6225_", at = @At("HEAD"), cancellable = true)
    private void empirecraft$onlyOnFuel(UseOnContext context, CallbackInfoReturnable<InteractionResult> reply) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (FireRules.combustible(level, pos, context.getClickedFace())
            || CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) return;
        reply.setReturnValue(InteractionResult.PASS);
    }
}
