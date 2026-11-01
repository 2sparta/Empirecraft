package ua.townyrecruits.mixin;
import ua.townyrecruits.GunGoal;
import com.talhanation.recruits.entities.ai.RecruitRangedCrossbowAttackGoal;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=RecruitRangedCrossbowAttackGoal.class,remap=false)
public abstract class CrossbowMixin {
    @Inject(method="isCrossbow",at=@At("HEAD"),cancellable=true)
    private static void gun(ItemStack s,CallbackInfoReturnable<Boolean> ci){if(GunGoal.gun(s))ci.setReturnValue(false);}
}
