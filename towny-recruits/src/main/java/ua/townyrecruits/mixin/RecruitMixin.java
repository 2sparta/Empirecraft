package ua.townyrecruits.mixin;

import ua.townyrecruits.*;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsGroup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.Container;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=AbstractRecruitEntity.class,remap=false)
public abstract class RecruitMixin {
    @Unique private long townyrecruits$lastSearch=-20;
    @Inject(method="hire",at=@At("HEAD"),cancellable=true)
    private void hire(Player p,RecruitsGroup group,boolean message,CallbackInfoReturnable<Boolean> ci){
        AbstractRecruitEntity r=(AbstractRecruitEntity)(Object)this;
        MilitarySystem military=MilitarySystem.current();
        if(group!=null && military!=null && (MilitarySystem.protectedGroup(group.getUUID()) && !group.getPlayerUUID().equals(p.m_20148_()) || military.data.squad(group.getUUID()).routing)){ci.setReturnValue(false);return;}
        if(!r.m_20193_().m_5776_() && (Bridge.INSTANCE==null || !Bridge.INSTANCE.canHire(p,r,true)))ci.setReturnValue(false);
    }
    @Inject(method="hire",at=@At("RETURN"))
    private void hired(Player p,RecruitsGroup group,boolean message,CallbackInfoReturnable<Boolean> ci){if(ci.getReturnValueZ() && Bridge.INSTANCE!=null)Bridge.INSTANCE.hired(p,(AbstractRecruitEntity)(Object)this);}
    @Inject(method="disband",at=@At("TAIL"))
    private void dismiss(Player p,boolean keep,boolean cost,CallbackInfo ci){if(Bridge.INSTANCE!=null)Bridge.INSTANCE.dataRemove((AbstractRecruitEntity)(Object)this);}
    @Inject(method={"checkPayment","doNoPaymentAction"},at=@At("HEAD"),cancellable=true)
    private void salary(CallbackInfo ci){ci.cancel();}
    @Inject(method="shouldAttack",at=@At("HEAD"),cancellable=true)
    private void target(LivingEntity target,CallbackInfoReturnable<Boolean> ci){
        if(target==null)return;AbstractRecruitEntity r=(AbstractRecruitEntity)(Object)this;
        if(!ua.townyrecruits.tactics.TacticalCombat.allowedTarget(r,target)){ci.setReturnValue(false);return;}
        Boolean allowed=Bridge.combat(r,target);
        if(allowed!=null)ci.setReturnValue(allowed && target.m_6084_() && r.getState()!=3 && r.m_6779_(target));
    }
    @Inject(method="searchForTargets",at=@At("HEAD"),cancellable=true)
    private void throttle(CallbackInfo ci){AbstractRecruitEntity r=(AbstractRecruitEntity)(Object)this;long now=r.m_20193_().m_46467_();if(now-townyrecruits$lastSearch<20)ci.cancel();else townyrecruits$lastSearch=now;}
    @Inject(method="m_8119_",at=@At("TAIL"))
    private void tacticalFacing(CallbackInfo ci){AbstractRecruitEntity r=(AbstractRecruitEntity)(Object)this;if(!r.m_20193_().m_5776_())ua.townyrecruits.tactics.TacticalCombat.postTick(r);}
    @Inject(method="assignToPlayer",at=@At("HEAD"),cancellable=true)
    private void transfer(UUID owner,UUID group,CallbackInfo ci){
        if(MilitarySystem.controlledTransfer((AbstractRecruitEntity)(Object)this,owner,group)){ci.cancel();return;}
        Bridge b=Bridge.INSTANCE;if(b==null||!b.ready||!java.util.Objects.equals(b.nation((AbstractRecruitEntity)(Object)this),b.snapshot.residents().get(owner)))ci.cancel();
    }
    @Inject(method="setGroupUUID",at=@At("HEAD"),cancellable=true)
    private void regimentAssignment(UUID group,CallbackInfo ci){if(MilitarySystem.protectsAssignment((AbstractRecruitEntity)(Object)this,group))ci.cancel();}
}
