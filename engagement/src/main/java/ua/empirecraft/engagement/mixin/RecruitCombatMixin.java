package ua.empirecraft.engagement.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.engagement.EngagementRuntime;

@Pseudo @Mixin(targets="ua.townyrecruits.Bridge",remap=false)
public abstract class RecruitCombatMixin {
    @Inject(method="combat",at=@At("HEAD"),cancellable=true) private static void engagement(LivingEntity a,LivingEntity b,CallbackInfoReturnable<Boolean> ci){Boolean allowed=EngagementRuntime.recruitOverride(a,b);if(allowed!=null)ci.setReturnValue(allowed);}
}
