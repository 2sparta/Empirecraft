package ua.empirecraft.townymap;

import guardtest.OfflineMixinService;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

/** Applies the real production Mixin to the exact installed Kotlin ChatPlus class. */
public final class InteractiveMixinSmoke {
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("empirecraft-client.mixins.json");
        var service=(OfflineMixinService)MixinService.getService();
        var target=service.getClassNode("com.ebicep.chatplus.features.chattabs.ChatTab");
        if(!service.transformer().transformClass(env,target.name.replace('/','.'),target))throw new AssertionError("No ChatTab injection");
        boolean component=false,strings=false;
        for(var method:target.methods) {
            if((method.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(target.name,method);
            if(!method.name.equals("matches"))continue;
            boolean hooked=false;
            for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call && call.name.contains("empirecraft$addressed"))hooked=true;
            if(method.desc.equals("(Lnet/minecraft/network/chat/Component;)Z"))component=hooked;
            if(method.desc.equals("(Ljava/lang/String;Ljava/lang/String;)Z"))strings=hooked;
        }
        if(!component||strings)throw new AssertionError("Wrong matches overload injected");
        System.out.println("INTERACTIVE_MIXIN_PASS real_transform=true component_hooked=true string_overload_untouched=true bytecode_verified=true");
    }
}
