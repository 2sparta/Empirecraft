package ua.townyrecruits.mixin;
import com.talhanation.recruits.entities.ai.compat.RecruitRangedMusketAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=RecruitRangedMusketAttackGoal.class,remap=false)
public abstract class NativeGunMixin {
    @Inject(method="m_8036_",at=@At("HEAD"),cancellable=true)
    private void customGun(CallbackInfoReturnable<Boolean> ci){ci.setReturnValue(false);}
}
