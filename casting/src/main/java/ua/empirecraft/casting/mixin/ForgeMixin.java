package ua.empirecraft.casting.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.*;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;import net.minecraftforge.items.ItemStackHandler;import net.minecraft.world.item.ItemStack;
@Mixin(value=CharcoalForgeBlockEntity.class,remap=false)
public abstract class ForgeMixin {
 @Inject(method="getSlotStackLimit",at=@At("HEAD"),cancellable=true)private void capacity(int slot,CallbackInfoReturnable<Integer> cir){if(slot>=0&&slot<=4)cir.setReturnValue(16);}
 @Redirect(method="consumeFuel",at=@At(value="INVOKE",target="Lnet/minecraftforge/items/ItemStackHandler;setStackInSlot(ILnet/minecraft/world/item/ItemStack;)V"))private void oneFuel(ItemStackHandler inventory,int slot,ItemStack ignored){inventory.extractItem(slot,1,false);}
}
