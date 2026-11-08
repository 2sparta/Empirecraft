package guardtest;

import java.util.List;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

public final class TacticalServerMixinTest {
    public static void main(String[] args) throws Exception {
        verify(MixinEnvironment.Side.SERVER, List.of("net.minecraft.world.entity.Entity", "net.minecraft.world.inventory.Slot",
            "com.talhanation.recruits.items.RecruitsSpawnEgg",
            "com.talhanation.recruits.inventory.RecruitSimpleContainer",
            "com.talhanation.recruits.inventory.RecruitInventoryMenu",
            "com.talhanation.recruits.inventory.RecruitInventoryMenu$1",
            "com.talhanation.recruits.inventory.RecruitInventoryMenu$2",
            "com.talhanation.recruits.inventory.RecruitInventoryMenu$3",
            "com.talhanation.recruits.CommandEvents", "net.minecraft.world.entity.ai.navigation.PathNavigation",
            "com.talhanation.recruits.entities.ai.RecruitRangedBowAttackGoal",
            "com.talhanation.recruits.entities.ai.RecruitRangedCrossbowAttackGoal",
            "com.talhanation.recruits.world.RecruitsGroupsManager",
            "com.talhanation.recruits.network.MessageDisbandGroup",
            "com.talhanation.recruits.network.MessageAssignGroupToPlayer",
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
            "com.talhanation.recruits.network.MessageApplyNoGroup"));
    }
    static void verify(MixinEnvironment.Side side, List<String> targets) throws Exception {
        MixinBootstrap.init(); var env = MixinEnvironment.getDefaultEnvironment(); env.setSide(side);
        Mixins.addConfiguration("townyrecruits.mixins.json");
        var service = (OfflineMixinService)MixinService.getService(); var transformer = service.transformer();
        for (String name : targets) {
            ClassNode node = service.getClassNode(name);
            if (!transformer.transformClass(env, name, node)) throw new AssertionError("No tactical injection: " + name);
            for (MethodNode method : node.methods) if ((method.access & 0x0500) == 0)
                new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
            System.out.println("PASS tactical " + side + " real-JAR Mixin transform and ASM verification: " + name);
        }
    }
}
