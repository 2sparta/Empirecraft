package ua.empirecraft.claimguard.mixin;

import net.mehvahdjukaar.supplementaries.common.items.BombItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.claimguard.BombCooldown;

@Mixin(value = BombItem.class, remap = false)
public abstract class BombItemMixin {
    @Inject(method = "m_7203_", at = @At("HEAD"), cancellable = true)
    private void empirecraft$checkCooldown(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!level.m_5776_() && BombCooldown.active(player)) {
            cir.setReturnValue(InteractionResultHolder.m_19100_(player.m_21120_(hand)));
        }
    }

    @Inject(method = "m_7203_", at = @At("RETURN"))
    private void empirecraft$sharedCooldown(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!level.m_5776_() && cir.getReturnValue().m_19089_().m_19077_()) BombCooldown.start(player);
    }
}
