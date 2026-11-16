package ua.empirecraft.armory.mixin;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.armory.Parts;
/** Combining damaged pieces must not erase poor quality into untagged baseline armor. */
@Mixin(value=RepairItemRecipe.class,remap=false)
public abstract class ArmorRepairMixin {
    @Inject(method="m_5874_(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;",at=@At("RETURN"),require=1)
    private void armory$keepRepairQuality(CraftingContainer grid,RegistryAccess access,CallbackInfoReturnable<ItemStack> cir){
        var result=cir.getReturnValue();if(!(result.getItem() instanceof ArmorItem))return;ItemStack weakest=ItemStack.EMPTY;int minimum=5;
        for(int i=0;i<grid.getContainerSize();i++){var s=grid.getItem(i);if(!(s.getItem() instanceof ArmorItem))continue;int quality=s.hasTag()&&s.getTag().contains("ForgingQuality")?Parts.quality(s):2;if(quality<minimum){minimum=quality;weakest=s;}}
        if(weakest.isEmpty())return;double wear=(double)result.getDamageValue()/Math.max(1,result.getMaxDamage());
        if(weakest.hasTag()&&weakest.getTag().contains("ForgingQuality"))result.getOrCreateTag().putString("ForgingQuality",Parts.QUALITY.get(minimum));
        if(weakest.hasTag()&&weakest.getTag().contains(Parts.DATA))result.getOrCreateTag().put(Parts.DATA,weakest.getTag().getCompound(Parts.DATA).copy());
        result.setDamageValue((int)Math.floor(wear*result.getMaxDamage()));
    }
}
