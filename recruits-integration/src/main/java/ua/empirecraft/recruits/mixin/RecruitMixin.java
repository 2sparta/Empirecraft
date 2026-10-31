package ua.empirecraft.recruits.mixin;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.recruits.*;

@Mixin(value=AbstractRecruitEntity.class,remap=false)
public abstract class RecruitMixin {
    @Inject(method="needsToGetFood",at=@At("HEAD"),cancellable=true)
    private void army$noPrivateFood(CallbackInfoReturnable<Boolean> ci){if(ArmyHooks.managed((AbstractRecruitEntity)(Object)this))ci.setReturnValue(false);}
    @Inject(method="updateTeam",at=@At("HEAD"),cancellable=true)
    private void army$allegiance(CallbackInfo ci){if(ArmyHooks.managed((AbstractRecruitEntity)(Object)this))ci.cancel();}
    @Inject(method="shouldAttack",at=@At("HEAD"),cancellable=true)
    private void army$target(LivingEntity target,CallbackInfoReturnable<Boolean> ci){var self=(AbstractRecruitEntity)(Object)this;if(ArmyHooks.managed(self)&&target!=null){if(!ArmyHooks.allows(self,target))ci.setReturnValue(false);else if(self.getState()!=3&&!(target instanceof net.minecraft.world.entity.monster.Enemy))ci.setReturnValue(true);}}
    @Inject(method="isOwnedBy",at=@At("HEAD"),cancellable=true)
    private void army$governor(Player player,CallbackInfoReturnable<Boolean> ci){var self=(AbstractRecruitEntity)(Object)this;if(ArmyHooks.garrison(self))ci.setReturnValue(ArmyHooks.command(player.m_20148_(),self));}
    @Inject(method="isEffectedByCommand(Ljava/util/UUID;Ljava/util/UUID;)Z",at=@At("HEAD"),cancellable=true)
    private void army$orders(UUID claimedPlayer,UUID group,CallbackInfoReturnable<Boolean> ci){var self=(AbstractRecruitEntity)(Object)this;UUID actor=ArmyHooks.SENDER.get();if(!ArmyHooks.managed(self)||actor==null)return;
        if(!ArmyHooks.command(actor,self))ci.setReturnValue(false);else if(ArmyHooks.garrison(self))ci.setReturnValue(self.m_6084_()&&self.getListen()&&(group==null||group.equals(self.getGroup())));
    }
    @Inject(method="assignToPlayer",at=@At("HEAD"),cancellable=true)
    private void army$noTheft(UUID newOwner,UUID group,CallbackInfo ci){var self=(AbstractRecruitEntity)(Object)this;UUID actor=ArmyHooks.SENDER.get();if(ArmyHooks.garrison(self)||actor!=null&&ArmyHooks.managed(self)&&!ArmyHooks.command(actor,self))ci.cancel();}
    @Inject(method="assignToPlayer",at=@At("RETURN"))
    private void army$registerHire(UUID newOwner,UUID group,CallbackInfo ci){var self=(AbstractRecruitEntity)(Object)this;if(!self.m_9236_().m_5776_()&&ArmyHooks.managed(self))ArmyHooks.observe(self);}
    @Inject(method="checkPayment",at=@At("HEAD"),cancellable=true)
    private void army$ledgerWages(Container chest,CallbackInfo ci){if(ArmyHooks.garrison((AbstractRecruitEntity)(Object)this))ci.cancel();}
    @Inject(method="doNoPaymentAction",at=@At("HEAD"),cancellable=true)
    private void army$noDesertion(CallbackInfo ci){if(ArmyHooks.garrison((AbstractRecruitEntity)(Object)this))ci.cancel();}
}
