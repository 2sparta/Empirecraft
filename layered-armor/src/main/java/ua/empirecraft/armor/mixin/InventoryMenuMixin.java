package ua.empirecraft.armor.mixin;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.armor.*;
@Mixin(value=InventoryMenu.class,remap=false)
public abstract class InventoryMenuMixin extends AbstractContainerMenu implements LayerInventoryAccess {
    @Unique private LayerInventory empirecraft$layers;
    protected InventoryMenuMixin(MenuType<?> type,int id){super(type,id);}
    @Inject(remap=false,method="<init>",at=@At("RETURN"))
    private void attach(Inventory inventory,boolean active,Player player,CallbackInfo ci){empirecraft$layers=new LayerInventory(player,this,this::addSlot);}
    @Override public LayerInventory empirecraft$layers(){return empirecraft$layers;}
}
