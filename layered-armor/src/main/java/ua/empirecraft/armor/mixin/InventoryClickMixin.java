package ua.empirecraft.armor.mixin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One intercept per native click, so packet order and desync checks stay intact. */
@Mixin(value=AbstractContainerMenu.class)
public abstract class InventoryClickMixin {
    @Inject(method="clicked",at=@At("HEAD"),cancellable=true)
    private void armorLayers(int slot,int button,ClickType click,Player player,CallbackInfo ci){if(((Object)this instanceof LayerInventoryAccess access&&access.empirecraft$layers()!=null&&access.empirecraft$layers().click(slot,click,player))||InventoryClicks.handle((AbstractContainerMenu)(Object)this,slot,button,click,player))ci.cancel();}
    @Inject(method="slotsChanged",at=@At("HEAD"))
    private void refreshLayers(CallbackInfo ci){if((Object)this instanceof LayerInventoryAccess access&&access.empirecraft$layers()!=null)access.empirecraft$layers().refresh();}
}
