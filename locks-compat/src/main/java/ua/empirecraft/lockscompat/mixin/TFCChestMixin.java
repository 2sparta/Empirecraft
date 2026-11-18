package ua.empirecraft.lockscompat.mixin;

import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="net.dries007.tfc.common.blockentities.TFCChestBlockEntity",remap=false)
public abstract class TFCChestMixin {
    @Inject(method="m_6643_",at=@At("HEAD"),cancellable=true)
    private void size(CallbackInfoReturnable<Integer> result){result.setReturnValue(27);}
    // Keep TFC's size restrictions, loot unpacking and pest behavior. Only change the grid.
    @Redirect(method="m_6555_",at=@At(value="INVOKE",target="Lnet/minecraftforge/registries/RegistryObject;get()Ljava/lang/Object;"))
    private Object menuType(RegistryObject<?> original){return MenuType.f_39959_;}
    @ModifyConstant(method="m_6555_",constant=@Constant(intValue=2))
    private int rows(int original){return 3;}
}
