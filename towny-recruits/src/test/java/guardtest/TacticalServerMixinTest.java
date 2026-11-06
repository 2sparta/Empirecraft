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
            "com.talhanation.recruits.inventory.RecruitSimpleContainer",
            "com.talhanation.recruits.inventory.RecruitInventoryMenu$1",
            "com.talhanation.recruits.inventory.RecruitInventoryMenu$2",
            "com.talhanation.recruits.inventory.RecruitInventoryMenu$3",
            "com.talhanation.recruits.CommandEvents", "net.minecraft.world.entity.ai.navigation.PathNavigation",
            "com.talhanation.recruits.entities.ai.RecruitRangedBowAttackGoal",
            "com.talhanation.recruits.entities.ai.RecruitRangedCrossbowAttackGoal",
            "com.talhanation.recruits.world.RecruitsGroupsManager",
            "com.talhanation.recruits.network.MessageDisbandGroup",
            "com.talhanation.recruits.network.MessageAssignGroupToPlayer"));
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
