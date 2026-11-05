package ua.townyrecruits.mixin;

import com.talhanation.recruits.entities.*;
import com.talhanation.recruits.inventory.RecruitSimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.townyrecruits.tactics.*;

/** Enforces capacity for native pickups and Forge InvWrapper automation as well as GUI slots. */
@Mixin(value = RecruitSimpleContainer.class, remap = false)
public abstract class BombContainerMixin implements RecruitBombInventory {
    @Shadow @Final private AbstractInventoryEntity recruit;
    @Override public boolean townyrecruits$isRecruitInventory() { return recruit instanceof AbstractRecruitEntity; }
    @Inject(method = "m_7013_", at = @At("HEAD"), cancellable = true)
    private void bombPlacement(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (townyrecruits$isRecruitInventory() && RecruitBombs.bomb(stack))
            ci.setReturnValue(slot >= 6 && RecruitBombs.count((Container)(Object)this, -1) == 0);
    }
    @Inject(method = "m_19173_", at = @At("HEAD"), cancellable = true)
    private void pickUpOne(ItemStack incoming, CallbackInfoReturnable<ItemStack> ci) {
        if (!townyrecruits$isRecruitInventory() || !RecruitBombs.bomb(incoming)) return;
        Container inventory = (Container)(Object)this;
        ItemStack remainder = incoming.m_41777_();
        if (RecruitBombs.count(inventory, -1) == 0) for (int slot = 6; slot < inventory.m_6643_(); slot++) {
            if (!inventory.m_8020_(slot).m_41619_()) continue;
            ItemStack one = incoming.m_41777_(); one.m_41764_(1);
            inventory.m_6836_(slot, one); remainder.m_41774_(1); break;
        }
        ci.setReturnValue(remainder);
    }
}
