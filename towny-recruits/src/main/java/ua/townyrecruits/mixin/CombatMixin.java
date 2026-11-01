package ua.townyrecruits.mixin;
import ua.townyrecruits.Bridge;
import com.talhanation.recruits.RecruitEvents;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=RecruitEvents.class,remap=false)
public abstract class CombatMixin {
    @Inject(method={"canAttack","canHarmTeam","canHarmTeamNoFriendlyFire"},at=@At("HEAD"),cancellable=true)
    private static void relation(LivingEntity a,LivingEntity b,CallbackInfoReturnable<Boolean> ci){if(a!=null&&b!=null){Boolean result=Bridge.combat(a,b);if(result!=null)ci.setReturnValue(result);}}
}
