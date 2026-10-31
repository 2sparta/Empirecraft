package guardtest;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;
public final class ArmyMixinTest {
    public static void main(String[] args)throws Exception{
        MixinBootstrap.init();var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.SERVER);Mixins.addConfiguration("empirecraft_recruits.mixins.json");if(args.length>1)Mixins.addConfiguration(args[1]);
        var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();int classes=0,methods=0,handlers=0;
        List<String> names=new ArrayList<>(List.of("com.talhanation.recruits.RecruitEvents","com.talhanation.recruits.entities.AbstractRecruitEntity","com.talhanation.recruits.entities.ai.RecruitEatGoal","com.talhanation.recruits.world.RecruitsDiplomacyManager","net.minecraft.world.entity.projectile.Projectile"));
        for(String line:Files.readAllLines(Path.of(args[0])))if(!line.isBlank())names.add(line);
        for(String name:names){var node=service.getClassNode(name);if(!transformer.transformClass(env,name,node))throw new AssertionError("No transformation: "+name);int injected=0;
            for(MethodNode method:node.methods){if((method.access&0x0500)==0){new Analyzer<>(new BasicVerifier()).analyze(node.name,method);methods++;}if(method.name.contains("army$")){handlers++;injected++;}}
            if(name.equals("net.minecraft.world.entity.projectile.Projectile")&&args.length>1&&node.methods.stream().noneMatch(m->m.name.contains("empirecraft$rememberOwner")))throw new AssertionError("ClaimGuard projectile provenance missing");
            if(injected==0)throw new AssertionError("No army guards: "+name);classes++;
        }
        if(classes<90||handlers<190)throw new AssertionError("Too few transformations: "+classes+" "+handlers);
        System.out.println("ARMY_MIXIN_PASS "+classes+" actual Recruits classes; "+methods+" ASM-verified methods; "+handlers+" injected guards");
    }
}
