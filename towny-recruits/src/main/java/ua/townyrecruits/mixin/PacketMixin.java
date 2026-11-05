package ua.townyrecruits.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(targets={
 "com.talhanation.recruits.network.MessageCreateTeam",
 "com.talhanation.recruits.network.MessageSaveTeamSettings",
 "com.talhanation.recruits.network.MessageLeaveTeam",
 "com.talhanation.recruits.network.MessageRemoveFromTeam",
 "com.talhanation.recruits.network.MessageSendJoinRequestTeam",
 "com.talhanation.recruits.network.MessageAddPlayerToTeam",
 "com.talhanation.recruits.network.MessageAddRecruitToTeam",
 "com.talhanation.recruits.network.MessageAssignGroupToPlayer",
 "com.talhanation.recruits.network.MessageChangeDiplomacyStatus",
 "com.talhanation.recruits.network.MessageSendTreaty",
 "com.talhanation.recruits.network.MessageAnswerTreaty",
 "com.talhanation.recruits.network.MessageAddEmbargo",
 "com.talhanation.recruits.network.MessageAddEmbargoFaction",
 "com.talhanation.recruits.network.MessageRemoveEmbargo"
},remap=false)
public abstract class PacketMixin {
 @Inject(method="executeServerSide",at=@At("HEAD"),cancellable=true)
 private void townyOnly(CallbackInfo ci){ci.cancel();}
}
