package ua.townyrecruits.mixin;
import ua.townyrecruits.GunGoal;
import com.talhanation.recruits.entities.ai.RecruitRangedCrossbowAttackGoal;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=RecruitRangedCrossbowAttackGoal.class,remap=false)
public abstract class CrossbowMixin {
    @Shadow @Final private CrossBowmanEntity crossBowman;
    @Inject(method={"m_8036_","m_8045_"},at=@At("HEAD"),cancellable=true)
    private void controlledCrossbow(CallbackInfoReturnable<Boolean> ci){if(crossBowman.getIsOwned())ci.setReturnValue(false);}
    @Inject(method="isCrossbow",at=@At("HEAD"),cancellable=true)
    private static void gun(ItemStack s,CallbackInfoReturnable<Boolean> ci){if(GunGoal.gun(s))ci.setReturnValue(false);}
}
