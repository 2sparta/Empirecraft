package ua.townyrecruits.mixin;

import com.talhanation.recruits.entities.BowmanEntity;
import com.talhanation.recruits.entities.ai.RecruitRangedBowAttackGoal;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecruitRangedBowAttackGoal.class, remap = false)
public abstract class TacticalArcherMixin {
    @Shadow @Final private BowmanEntity recruit;
    @Inject(method = {"m_8036_", "m_8045_"}, at = @At("HEAD"), cancellable = true)
    private void controlledBow(CallbackInfoReturnable<Boolean> ci) { if (recruit.getIsOwned()) ci.setReturnValue(false); }
}
