package com.empirecraft.whalehitboxes.test;

import com.empirecraft.sails.test.JourneyMapMixinTest;
import java.nio.file.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.MixinService;

/** Transform actual Minecraft/Better Combat classes, without booting a graphical client. */
public final class WhaleMixinTest {
    public static void main(String[] args)throws Exception{
        MixinService.boot();var get=MixinService.class.getDeclaredMethod("getInstance");get.setAccessible(true);Object holder=get.invoke(null);
        var service=MixinService.class.getDeclaredField("service");service.setAccessible(true);service.set(holder,new JourneyMapMixinTest.Service());
        var properties=MixinService.class.getDeclaredField("propertyService");properties.setAccessible(true);properties.set(holder,new JourneyMapMixinTest.Properties());
        MixinBootstrap.init();var env=MixinEnvironment.getCurrentEnvironment();env.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("accuratehitboxes.mixins.json");Mixins.addConfiguration("accuratehitboxes.client.mixins.json");Mixins.addConfiguration("accuratehitboxes.empirecraft.mixins.json");
        var constructor=Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();constructor.setAccessible(true);var transformer=(IMixinTransformer)constructor.newInstance();
        String[] targets={"net.minecraft.world.entity.player.Player","net.minecraft.client.renderer.GameRenderer","net.minecraft.world.entity.projectile.ProjectileUtil",
                "net.bettercombat.client.collision.TargetFinder","net.bettercombat.client.collision.TargetFinder$CollisionFilter","net.bettercombat.client.collision.TargetFinder$RadialFilter"};
        String[] hooks={"inReach","query","query","query","collides","radial"};int count=args[0].equals("absent")?3:targets.length;
        for(int i=0;i<count;i++){
            String name=targets[i];byte[] before=WhaleMixinTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class").readAllBytes();
            byte[] after=transformer.transformClass(env,name,before);ClassNode node=new ClassNode();new ClassReader(after).accept(node,0);int matches=0;
            for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call){
                if(call.owner.startsWith("net/devra/accuratehitboxes/empirecraft/") && call.name.equals(hooks[i]))matches++;
                if(call.itf && call.owner.equals(node.name))throw new AssertionError("Invalid interface Methodref to merged class "+name);
            }
            if(matches!=1)throw new AssertionError(name+" hook="+hooks[i]+" count="+matches);
            System.out.println("WHALE_MIXIN_TARGET_PASS "+name);
        }
        System.out.println("WHALE_CLIENT_MIXINS_PASS mode="+args[0]+" targets="+count);
    }
}
