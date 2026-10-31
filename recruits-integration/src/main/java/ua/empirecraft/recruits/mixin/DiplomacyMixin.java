package ua.empirecraft.recruits.mixin;
import com.talhanation.recruits.world.RecruitsDiplomacyManager;
import com.talhanation.recruits.world.RecruitsDiplomacyManager.DiplomacyStatus;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.recruits.*;

@Mixin(value=RecruitsDiplomacyManager.class,remap=false)
public abstract class DiplomacyMixin {
    @Inject(method="setRelation(Ljava/lang/String;Ljava/lang/String;Lcom/talhanation/recruits/world/RecruitsDiplomacyManager$DiplomacyStatus;Lnet/minecraft/server/level/ServerLevel;Z)V",at=@At("HEAD"),cancellable=true)
    private void army$authoritativeDiplomacy(String a,String b,DiplomacyStatus status,ServerLevel level,boolean update,CallbackInfo ci){if(!FactionSync.AUTH.get()&&ArmyHooks.relationManaged(a,b))ci.cancel();}
}
