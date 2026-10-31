package ua.empirecraft.recruits.mixin;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsGroup;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.recruits.ArmyHooks;
import java.util.Map;

@Mixin(value=AbstractRecruitEntity.class,remap=false)
public abstract class RecruitHireMixin {
    @Inject(method="hire",at=@At("HEAD"),cancellable=true)
    private void army$capacity(Player player,RecruitsGroup group,boolean message,CallbackInfoReturnable<Boolean> ci){var r=(AbstractRecruitEntity)(Object)this;if(!r.m_9236_().m_5776_()&&(r.getIsOwned()||!Boolean.TRUE.equals(ArmyHooks.call("canHire",Map.of("actor",player.m_20148_().toString())).get("allow"))))ci.setReturnValue(false);}
    @Inject(method="hire",at=@At("RETURN"))
    private void army$register(Player player,RecruitsGroup group,boolean message,CallbackInfoReturnable<Boolean> ci){if(Boolean.TRUE.equals(ci.getReturnValue())&&!player.m_9236_().m_5776_())ArmyHooks.observe((AbstractRecruitEntity)(Object)this);}
}
