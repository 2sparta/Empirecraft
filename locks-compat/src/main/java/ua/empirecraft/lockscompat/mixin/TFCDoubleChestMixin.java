package ua.empirecraft.lockscompat.mixin;

import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraftforge.registries.RegistryObject;

@Mixin(targets="net.dries007.tfc.common.blocks.wood.TFCChestBlock$1$1",remap=false)
public abstract class TFCDoubleChestMixin {
    @Redirect(method="m_7208_",at=@At(value="INVOKE",target="Lnet/minecraftforge/registries/RegistryObject;get()Ljava/lang/Object;"))
    private Object menuType(RegistryObject<?> original){return MenuType.f_39962_;}
    @ModifyConstant(method="m_7208_",constant=@Constant(intValue=4))
    private int rows(int original){return 6;}
}
