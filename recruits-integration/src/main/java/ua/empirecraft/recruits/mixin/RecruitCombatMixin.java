package ua.empirecraft.recruits.mixin;
import com.talhanation.recruits.RecruitEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.recruits.ArmyHooks;

@Mixin(value=RecruitEvents.class,remap=false)
public abstract class RecruitCombatMixin {
    @Inject(method="canAttack",at=@At("HEAD"),cancellable=true)
    private static void army$combat(LivingEntity a,LivingEntity b,CallbackInfoReturnable<Boolean> ci){if(b!=null&&!ArmyHooks.allows(a,b))ci.setReturnValue(false);}
    @Inject(method="handleSignificantDamage",at=@At("HEAD"),cancellable=true)
    private void army$noAccidentalWar(LivingEntity a,LivingEntity b,double damage,ServerLevel level,CallbackInfo ci){if(ArmyHooks.relevant(a,b)||a.m_5647_()!=null&&a.m_5647_().m_5758_().startsWith("ec_")||b.m_5647_()!=null&&b.m_5647_().m_5758_().startsWith("ec_"))ci.cancel();}
}
