package ua.empirecraft.recruits.mixin;
import com.talhanation.recruits.entities.ai.RecruitEatGoal;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.recruits.ArmyHooks;

@Mixin(value=RecruitEatGoal.class,remap=false)
public abstract class RecruitFoodMixin {
    @Shadow public AbstractRecruitEntity recruit;
    @Inject(method="m_8036_",at=@At("HEAD"),cancellable=true)
    private void army$cityRations(CallbackInfoReturnable<Boolean> ci){if(!recruit.m_9236_().m_5776_()&&ArmyHooks.managed(recruit))ci.setReturnValue(false);}
}
