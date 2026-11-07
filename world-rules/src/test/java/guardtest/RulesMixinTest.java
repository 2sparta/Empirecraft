package guardtest;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

public final class RulesMixinTest {
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("empirecraft_world_rules.mixins.json");
        var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();
        String[] targets={"net.minecraft.world.item.ItemStack","net.minecraft.world.level.block.entity.BrewingStandBlockEntity","net.minecraft.world.inventory.EnchantmentMenu","me.toastymop.combatlog.CombatConfig","net.minecraft.world.entity.ai.goal.UseItemGoal","net.minecraft.world.entity.npc.WanderingTrader","net.minecraft.world.entity.npc.AbstractVillager"};
        for(String name:targets) {
            var node=service.getClassNode(name);
            if(!transformer.transformClass(env,name,node))throw new AssertionError("No injection into "+name);
            int hooks=0;
            for(MethodNode method:node.methods) {
                if((method.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
                if(method.name.contains("empirecraft$"))hooks++;
            }
            if(hooks==0)throw new AssertionError("Missing hooks "+name);
            System.out.println("PASS actual Mixin transformation and ASM verification: "+name+" ("+hooks+" helpers)");
        }
        var renewal=service.getClassNode("ua.empirecraft.worldrules.NaturalRenewal");
        for(MethodNode method:renewal.methods)for(AbstractInsnNode instruction:method.instructions) {
            if(instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/server/level/ServerChunkCache") && !call.name.equals("m_7131_") && !call.name.equals("m_8483_"))throw new AssertionError("Unexpected chunk access "+call.name);
        }
        System.out.println("PASS renewal uses getChunkNow; no generation/loading method is invoked");
    }
}
