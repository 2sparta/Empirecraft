package ua.townyrecruits.mixin;

import com.talhanation.recruits.CommandEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.townyrecruits.tactics.TacticalMovement;

@Mixin(value = CommandEvents.class, remap = false)
public abstract class TacticalCommandMixin {
    @Inject(method = "onMovementCommand(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/List;IIZZ)V", at = @At("HEAD"))
    private static void nativeMovement(ServerPlayer p, List<AbstractRecruitEntity> recruits, int state, int formation,
                                       boolean tight, boolean hold, CallbackInfo ci) { TacticalMovement.cancelAll(recruits); }
    @Inject(method = "onFaceCommand", at = @At("HEAD"))
    private static void nativeFace(ServerPlayer p, List<AbstractRecruitEntity> recruits, int formation,
                                  boolean tight, boolean hold, CallbackInfo ci) { TacticalMovement.cancelAll(recruits); }
}
