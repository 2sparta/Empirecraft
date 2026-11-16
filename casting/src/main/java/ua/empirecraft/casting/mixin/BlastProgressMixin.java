package ua.empirecraft.casting.mixin;
import java.util.List;
import net.dries007.tfc.common.blockentities.BlastFurnaceBlockEntity;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.common.recipes.BlastFurnaceRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.casting.Metallurgy;
/** Keep native heating/fuel/recipe yields, but process only one charge per cycle. */
@Mixin(value=BlastFurnaceBlockEntity.class,remap=false)
public abstract class BlastProgressMixin {
 @Shadow @Final private List<ItemStack> inputStacks;
 @Shadow @Final private FluidTank outputFluidTank;
 @Shadow private FluidStack inputFluid;
 @Unique private int casting$smeltProgress;
 @Unique private boolean casting$advanced;
 @Inject(method="serverTick",at=@At("HEAD"))
 private static void cycle(Level level,BlockPos pos,BlockState state,BlastFurnaceBlockEntity furnace,CallbackInfo ci){var self=(BlastProgressMixin)(Object)furnace;self.casting$advanced=false;if(self.inputStacks.isEmpty())self.casting$smeltProgress=0;}
 @Redirect(method="serverTick",at=@At(value="INVOKE",target="Lnet/dries007/tfc/common/recipes/HeatingRecipe;isValidTemperature(F)Z"))
 private static boolean oneCharge(HeatingRecipe recipe,float temperature,Level level,BlockPos pos,BlockState state,BlastFurnaceBlockEntity furnace){
  var self=(BlastProgressMixin)(Object)furnace;
  if(self.casting$advanced||!recipe.isValidTemperature(temperature)||self.outputFluidTank.getFluidAmount()>=self.outputFluidTank.getCapacity())return false;
  var feed=recipe.getDisplayOutputFluid().copy();
  if(!self.inputFluid.isEmpty()&&self.inputFluid.isFluidEqual(feed))feed.grow(self.inputFluid.getAmount());
  var conversion=BlastFurnaceRecipe.get(level,feed);
  if(conversion!=null){var output=conversion.assembleFluidOutput(feed);if(self.outputFluidTank.fill(output,FluidAction.SIMULATE)<output.getAmount())return false;}
  self.casting$advanced=true;
  if(++self.casting$smeltProgress<Metallurgy.BLAST_CYCLE_TICKS)return false;
  self.casting$smeltProgress=0;return true;
 }
 @Inject(method="m_183515_",at=@At("TAIL"))private void saveProgress(CompoundTag tag,CallbackInfo ci){tag.putInt("empirecraftSmeltProgress",casting$smeltProgress);}
 @Inject(method="loadAdditional",at=@At("TAIL"))private void loadProgress(CompoundTag tag,CallbackInfo ci){casting$smeltProgress=Math.max(0,Math.min(Metallurgy.BLAST_CYCLE_TICKS-1,tag.getInt("empirecraftSmeltProgress")));}
}
