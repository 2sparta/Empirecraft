package ua.empirecraft.forging.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ua.empirecraft.forging.RangedQuality;

@Mixin(targets = {"net.minecraft.world.item.CrossbowItem", "com.TBK.medieval_boomsticks.common.items.RechargeItem"}, remap = false)
public abstract class CrossbowQualityMixin {
    @Redirect(method = "m_40894_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean empirecraft$crossbowQuality(Level level, Entity projectile, Level originalLevel, LivingEntity shooter,
        InteractionHand hand, ItemStack weapon, ItemStack ammo, float pitch, boolean creative, float speed, float spread, float angle) {
        RangedQuality.mark(projectile, weapon);
        return level.m_7967_(projectile);
    }
}
