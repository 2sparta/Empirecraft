package ua.empirecraft.armory.mixin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.RegistryAccess;
import net.minecraftforge.items.ItemStackHandler;
import net.stirdrem.overgeared.block.entity.AbstractSmithingAnvilBlockEntity;
import net.stirdrem.overgeared.ForgingQuality;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.armory.*;
@Mixin(value=AbstractSmithingAnvilBlockEntity.class,remap=false)
public abstract class PartForgingMixin {
    @Shadow protected ItemStackHandler itemHandler;
    @Redirect(method="craftItem",at=@At(value="INVOKE",target="Lnet/minecraftforge/items/ItemStackHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;"),require=1)
    private ItemStack armory$keepPattern(ItemStackHandler inventory,int slot,int amount,boolean simulate){if(inventory.getStackInSlot(slot).getItem() instanceof PatternItem)return inventory.getStackInSlot(slot).copyWithCount(amount);return inventory.extractItem(slot,amount,simulate);}
    @Inject(method="rollFailure",at=@At("HEAD"),cancellable=true,require=1)
    private void armory$keepReworkablePart(ForgingQuality quality,CallbackInfoReturnable<Boolean> cir){var recipe=((AbstractSmithingAnvilBlockEntity)(Object)this).getCurrentRecipe();if(recipe.isPresent()&&recipe.get().getResultItem(RegistryAccess.EMPTY).getItem() instanceof PartItem)cir.setReturnValue(false);}
    @Inject(method="transferIngredientNBT",at=@At("RETURN"),require=1)
    private void armory$deterministicPartQuality(ItemStack result,net.stirdrem.overgeared.recipe.ForgingRecipe recipe,CallbackInfo ci){
        if(!(result.getItem() instanceof PartItem part))return;
        var blueprint=itemHandler.getStackInSlot(11);int ceiling=1;
        if(blueprint.getItem() instanceof net.stirdrem.overgeared.item.custom.BlueprintItem&&blueprint.hasTag()&&Parts.blueprints(part.form).contains(blueprint.getTag().getString("ToolType")))ceiling=Math.max(0,Parts.QUALITY.indexOf(blueprint.getTag().getString("Quality")));
        int quality=Parts.quality(result);if(ceiling==4&&quality>=3)quality=4;
        result.getOrCreateTag().putString("ForgingQuality",Parts.QUALITY.get(Math.min(ceiling,quality)));result.getOrCreateTag().putInt(Parts.TREATMENT,0);
    }
}
