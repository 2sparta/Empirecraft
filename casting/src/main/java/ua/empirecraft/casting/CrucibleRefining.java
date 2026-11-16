package ua.empirecraft.casting;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.util.Alloy;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import ua.empirecraft.casting.mixin.InventoryAccess;
/** Refine the raw alloy components, including pig iron added to an existing steel bath. */
public final class CrucibleRefining {
 private static final String PIG="tfc:pig_iron",STEEL="tfc:steel";
 public static boolean isFlux(net.minecraft.world.item.ItemStack stack){return Thermal.id(stack).equals("tfc:powder/flux");}
 public static void step(CrucibleBlockEntity crucible) {
  if(crucible.getLevel()==null||crucible.getLevel().isClientSide||crucible.getTemperature()<1540)return;
  if(!(crucible.getAlloy() instanceof Alloy alloy))return;
  // The result fluid can be 'unknown' for steel + pig iron; never drain that mixture.
  // Native NBT retains exact fractional component amounts after partial pouring.
  var state=alloy.serializeNBT();var contents=state.getCompound("contents");double pig=contents.getDouble(PIG);
  if(pig<=0)return;
  for(String metal:contents.getAllKeys())if(!metal.equals(PIG)&&!metal.equals(STEEL)&&contents.getDouble(metal)>Alloy.EPSILON)return;
  int required=(int)Math.ceil(pig/1000d),available=0;
  IItemHandlerModifiable inventory=((InventoryAccess)crucible).casting$inventory();
  for(int i=0;i<9;i++)if(isFlux(inventory.getStackInSlot(i)))available+=inventory.getStackInSlot(i).getCount();
  if(available<required)return;
  contents.putDouble(STEEL,contents.getDouble(STEEL)+pig);contents.remove(PIG);
  alloy.deserializeNBT(state); // Rebuild TFC's result cache without changing volume/capacity.
  for(int i=0;i<9&&required>0;i++){var stack=inventory.getStackInSlot(i);if(isFlux(stack)){int used=Math.min(required,stack.getCount());var left=stack.copy();left.shrink(used);inventory.setStackInSlot(i,left);required-=used;}}
  crucible.setChanged();crucible.markForSync();
 }
}
