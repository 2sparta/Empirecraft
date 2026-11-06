package ua.townyrecruits.mixin;

import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.townyrecruits.ExchangeGuards;

@Mixin(targets={
 "com.talhanation.recruits.network.MessageRecruitGui",
 "com.talhanation.recruits.network.MessageDebugGui",
 "com.talhanation.recruits.network.MessageAggroGui",
 "com.talhanation.recruits.network.MessageFollowGui",
 "com.talhanation.recruits.network.MessageMountEntityGui",
 "com.talhanation.recruits.network.MessageDismountGui",
 "com.talhanation.recruits.network.MessageClearTargetGui",
 "com.talhanation.recruits.network.MessageClearUpkeepGui",
 "com.talhanation.recruits.network.MessageDebugScreen",
 "com.talhanation.recruits.network.MessageDisband",
 "com.talhanation.recruits.network.MessageListen",
 "com.talhanation.recruits.network.MessagePatrolLeaderSetPatrolState",
 "com.talhanation.recruits.network.MessageWriteSpawnEgg",
 "com.talhanation.recruits.network.MessageAggro",
 "com.talhanation.recruits.network.MessageAttack",
 "com.talhanation.recruits.network.MessageBackToMountEntity",
 "com.talhanation.recruits.network.MessageClearTarget",
 "com.talhanation.recruits.network.MessageClearUpkeep",
 "com.talhanation.recruits.network.MessageDismount",
 "com.talhanation.recruits.network.MessageFaceCommand",
 "com.talhanation.recruits.network.MessageFormationFollowMovement",
 "com.talhanation.recruits.network.MessageMountEntity",
 "com.talhanation.recruits.network.MessageMovement",
 "com.talhanation.recruits.network.MessageProtectEntity",
 "com.talhanation.recruits.network.MessageRangedFire",
 "com.talhanation.recruits.network.MessageRest",
 "com.talhanation.recruits.network.MessageShields",
 "com.talhanation.recruits.network.MessageStrategicFire",
 "com.talhanation.recruits.network.MessageUpkeepEntity",
 "com.talhanation.recruits.network.MessageUpkeepPos",
 "com.talhanation.recruits.network.MessageGroup",
 "com.talhanation.recruits.network.MessageAssignRecruitToPlayer",
 "com.talhanation.recruits.network.MessageSetLeaderGroup",
 "com.talhanation.recruits.network.MessageAssignGroupToCompanion",
 "com.talhanation.recruits.network.MessageRemoveAssignedGroupFromCompanion",
 "com.talhanation.recruits.network.MessageApplyNoGroup"
},remap=false)
public abstract class ExchangePacketMixin {
    @Inject(method="executeServerSide",at=@At("HEAD"),cancellable=true)
    private void authentic(NetworkEvent.Context context,CallbackInfo ci){if(!ExchangeGuards.packet(this,context))ci.cancel();}
}
