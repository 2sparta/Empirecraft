package ua.empirecraft.casting;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.casting.mixin.InventoryAccess;
/** Full-batch refining keeps native alloy bookkeeping and total metal volume intact. */
public final class CrucibleRefining {
 public static void step(CrucibleBlockEntity crucible) {
  if(crucible.getLevel()==null||crucible.getLevel().isClientSide||crucible.getTemperature()<1540)return;
  var tank=crucible.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElse(null);
  if(tank==null)return;
  var liquid=tank.getFluidInTank(0);var id=ForgeRegistries.FLUIDS.getKey(liquid.getFluid());
  if(liquid.isEmpty()||!new ResourceLocation("tfc:metal/pig_iron").equals(id))return;
  int required=(liquid.getAmount()+999)/1000,available=0;
  IItemHandlerModifiable inventory=((InventoryAccess)crucible).casting$inventory();
  var flux=ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfc:powder/flux"));
  for(int i=0;i<9;i++)if(inventory.getStackInSlot(i).is(flux))available+=inventory.getStackInSlot(i).getCount();
  if(available<required)return;
  var drained=tank.drain(liquid.getAmount(),FluidAction.SIMULATE);
  if(drained.getAmount()!=liquid.getAmount())return;
  tank.drain(liquid.getAmount(),FluidAction.EXECUTE);
  tank.fill(new FluidStack(ForgeRegistries.FLUIDS.getValue(new ResourceLocation("tfc:metal/steel")),liquid.getAmount()),FluidAction.EXECUTE);
  for(int i=0;i<9&&required>0;i++){var stack=inventory.getStackInSlot(i);if(stack.is(flux)){int used=Math.min(required,stack.getCount());var left=stack.copy();left.shrink(used);inventory.setStackInSlot(i,left);required-=used;}}
  crucible.setChanged();crucible.markForSync();
 }
}
