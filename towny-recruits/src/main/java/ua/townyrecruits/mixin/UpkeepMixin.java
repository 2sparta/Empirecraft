package ua.townyrecruits.mixin;
import com.talhanation.recruits.entities.ai.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Replaced by a bounded, claim-checked supply service; no unguarded fallback chest scan. */
@Mixin(value={RecruitUpkeepPosGoal.class,RecruitUpkeepEntityGoal.class},remap=false)
public abstract class UpkeepMixin {
    @Inject(method={"m_8036_","m_8045_"},at=@At("HEAD"),cancellable=true)
    private void supplies(CallbackInfoReturnable<Boolean> ci){ci.setReturnValue(false);}
}
