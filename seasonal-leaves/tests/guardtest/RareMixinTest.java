package guardtest;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;
public final class RareMixinTest {
    public static void main(String[] args)throws Exception{
        MixinBootstrap.init();var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.SERVER);
        // Verify coexistence with the previously installed calendar's survival redirect.
        Mixins.addConfiguration("empirecraft_calendar.mixins.json");
        Mixins.addConfiguration("empirecraft_seasonal_vegetation.mixins.json");
        var service=(OfflineMixinService)MixinService.getService();var node=service.getClassNode("ua.empirecraft.worldrules.NaturalRenewal");
        if(!service.transformer().transformClass(env,"ua.empirecraft.worldrules.NaturalRenewal",node))throw new AssertionError("No transformation");
        int rare=0,calendar=0;
        for(MethodNode method:node.methods){
            if((method.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
            if(method.name.contains("filterRare"))rare++;
            if(method.name.contains("seasonalRenewal"))calendar++;
        }
        if(rare!=1 || calendar!=1)throw new AssertionError("Both policies must be present: "+rare+"/"+calendar);
        System.out.println("RARE_MIXIN_PASS actual World Rules 1.0.2 and Calendar 1.1.1 transformed together; ASM verified");
    }
}
