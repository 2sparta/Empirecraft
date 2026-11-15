package ua.empirecraft.casting.mixin;
import net.stirdrem.overgeared.block.entity.AbstractSmithingAnvilBlockEntity;import net.stirdrem.overgeared.recipe.ForgingRecipe;
import net.minecraft.core.RegistryAccess;import net.minecraft.world.item.ItemStack;import net.minecraftforge.items.ItemStackHandler;import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import ua.empirecraft.casting.Thermal;
@Mixin(value=AbstractSmithingAnvilBlockEntity.class,remap=false)
public abstract class AnvilHeatMixin {
 @Shadow @Final protected ItemStackHandler itemHandler;
 @Redirect(method="craftItem",at=@At(value="INVOKE",target="Lnet/stirdrem/overgeared/recipe/ForgingRecipe;m_8043_(Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;"))
 private ItemStack hotResult(ForgingRecipe recipe,RegistryAccess access){var result=recipe.getResultItem(access);float temperature=0;for(int i=0;i<9;i++)temperature=Math.max(temperature,HeatCapability.getTemperature(itemHandler.getStackInSlot(i)));HeatCapability.setTemperature(result,temperature);return result;}
}
