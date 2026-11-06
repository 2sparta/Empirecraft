package ua.townyrecruits.mixin;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsGroup;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.townyrecruits.*;

@Mixin(value=AbstractRecruitEntity.class,remap=false)
public abstract class ExchangeRecruitMixin {
    @Inject(method={"openGUI","openDebugScreen"},at=@At("HEAD"),cancellable=true)
    private void equipment(Player player,CallbackInfo ci){if(!ExchangeGuards.controls((AbstractRecruitEntity)(Object)this,player))ci.cancel();}
    @Inject(method="isEffectedByCommand(Ljava/util/UUID;Ljava/util/UUID;)Z",at=@At("HEAD"),cancellable=true)
    private void orders(UUID player,UUID group,CallbackInfoReturnable<Boolean> ci){if(!RegimentExchange.mayControl((AbstractRecruitEntity)(Object)this,player))ci.setReturnValue(false);}
    @Inject(method="hire",at=@At("HEAD"),cancellable=true)
    private void hire(Player player,RecruitsGroup group,boolean message,CallbackInfoReturnable<Boolean> ci){if(RegimentExchange.captain(player.m_20148_())||group!=null&&RegimentExchange.locked(group.getUUID()))ci.setReturnValue(false);}
    @Inject(method="m_6071_",at=@At("HEAD"),cancellable=true)
    private void interaction(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> ci){var r=(AbstractRecruitEntity)(Object)this;if(r.getIsOwned()&&!ExchangeGuards.controls(r,player))ci.setReturnValue(InteractionResult.PASS);}
}
