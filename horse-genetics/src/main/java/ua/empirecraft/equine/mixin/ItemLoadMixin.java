package ua.empirecraft.equine.mixin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.equine.LegacyMigration;
@Mixin(value=ItemStack.class,remap=false)
public abstract class ItemLoadMixin {
    @Inject(method="m_41712_",at=@At("HEAD"))
    private static void migrate(CompoundTag tag,CallbackInfoReturnable<ItemStack> ci){LegacyMigration.item(tag);}
}
