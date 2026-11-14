package ua.empirecraft.casting.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraftforge.items.*;import net.dries007.tfc.common.blockentities.InventoryBlockEntity;import net.dries007.tfc.common.capabilities.SidedHandler;
@Mixin(value=InventoryBlockEntity.class,remap=false)
public interface InventoryAccess {
 @Accessor("inventory") IItemHandlerModifiable casting$inventory();
 @Accessor("sidedInventory") SidedHandler.Builder<IItemHandler> casting$sides();
}
