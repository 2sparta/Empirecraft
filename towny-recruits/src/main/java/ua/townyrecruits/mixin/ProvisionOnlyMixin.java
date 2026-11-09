package ua.townyrecruits.mixin;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Owned troops receive nutrition through the town ledger, never inventory/chest food. */
@Mixin(value=AbstractRecruitEntity.class,remap=false)
public abstract class ProvisionOnlyMixin {
    @Inject(method="canEatItemStack",at=@At("HEAD"),cancellable=true)
    private void townFood(ItemStack stack,CallbackInfoReturnable<Boolean> result){if(((AbstractRecruitEntity)(Object)this).getIsOwned())result.setReturnValue(false);}
}
