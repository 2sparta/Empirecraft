package ua.townyrecruits.mixin;
import ua.townyrecruits.Bridge;
import com.talhanation.recruits.world.RecruitsPlayerUnitManager;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=RecruitsPlayerUnitManager.class,remap=false)
public abstract class UnitLimitMixin {
    @Inject(method="canPlayerRecruit",at=@At("HEAD"),cancellable=true)
    private void cap(String team,UUID owner,CallbackInfoReturnable<Boolean> ci){Bridge b=Bridge.INSTANCE;if(b!=null){UUID n=b.snapshot.residents().get(owner);ci.setReturnValue(b.ready && b.censusReady() && b.recruitment.slots(n,owner));}}
    @Inject(method="getRemainingRecruitSlots",at=@At("HEAD"),cancellable=true)
    private void remaining(String team,UUID owner,CallbackInfoReturnable<Integer> ci){Bridge b=Bridge.INSTANCE;if(b!=null){UUID n=b.snapshot.residents().get(owner);ci.setReturnValue(!b.ready||!b.censusReady()||n==null?0:Math.max(0,Math.min(b.limit(n)-b.count(n),b.recruitment.perPlayer(n,owner)-b.recruitment.ownerCount(n,owner))));}}
}
