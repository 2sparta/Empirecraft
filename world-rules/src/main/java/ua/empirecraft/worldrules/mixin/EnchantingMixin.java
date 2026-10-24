package ua.empirecraft.worldrules.mixin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=EnchantmentMenu.class,remap=false)
public abstract class EnchantingMixin {
    @Inject(method="m_6366_",at=@At("HEAD"),cancellable=true)
    private void empirecraft$noEnchanting(Player player,int button,CallbackInfoReturnable<Boolean> ci) { ci.setReturnValue(false); }
}
