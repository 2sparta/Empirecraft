package com.empirecraft.sails.test;

import java.nio.file.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.MixinService;

/** Apply production common AND camera mixins to real Minecraft classes, without graphics. */
public final class FloodMixinTest {
    public static void main(String[] args)throws Exception {
        MixinService.boot();var singleton=MixinService.class.getDeclaredMethod("getInstance");singleton.setAccessible(true);Object holder=singleton.invoke(null);
        var service=MixinService.class.getDeclaredField("service");service.setAccessible(true);service.set(holder,new JourneyMapMixinTest.Service());
        var props=MixinService.class.getDeclaredField("propertyService");props.setAccessible(true);props.set(holder,new JourneyMapMixinTest.Properties());
        MixinBootstrap.init();
        var env=MixinEnvironment.getCurrentEnvironment();env.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("vs_sails-compat.mixins.json");
        var ctor=Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();ctor.setAccessible(true);var transformer=(IMixinTransformer)ctor.newInstance();
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        String[] names={"net.minecraft.client.Camera","net.minecraft.world.entity.Entity","net.minecraft.world.level.Level","net.minecraft.world.level.material.FlowingFluid","net.minecraft.world.level.block.DoorBlock","net.minecraft.world.level.block.TrapDoorBlock","net.minecraft.world.level.block.SimpleWaterloggedBlock","net.minecraft.client.renderer.block.LiquidBlockRenderer","me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer"};
        String[] hooks={"FloodQueryScope","FloodQueryScope","FloodQueryScope","ShipFlooding","DoorWater","DoorWater","FiniteBuckets","FloodWaterRenderer","FloodWaterRenderer"};
        Path output=Path.of(args[0]);Files.createDirectories(output);
        for(int i=0;i<names.length;i++){
            String name=names[i];byte[] before=FloodMixinTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class").readAllBytes();
            byte[] after=transformer.transformClass(env,name,before);ClassNode node=new ClassNode();new ClassReader(after).accept(node,0);int hooked=0;
            int localHeight=0,oceanClip=0;
            for(MethodNode m:node.methods)for(AbstractInsnNode insn:m.instructions)if(insn instanceof MethodInsnNode call){
                if(call.owner.endsWith("/"+hooks[i]))hooked++;
                if(call.owner.endsWith("/FloodEntityWater"))localHeight++;
                if(call.owner.endsWith("/OceanWaterClip"))oceanClip++;
            }
            if(hooked==0)throw new AssertionError("Missing finite-water hooks in "+name);
            if(name.endsWith(".Entity") && localHeight==0)throw new AssertionError("Missing ship-space Forge fluid-height wrapper");
            if((i==2 || i==7 || i==8) && oceanClip==0)throw new AssertionError("Missing moving-hull ocean exclusion in "+name);
            Files.write(output.resolve(name.substring(name.lastIndexOf('.')+1)+".class"),after);
            System.out.println("FLOOD_MIXIN_TARGET_PASS "+name+" hooks="+hooked);
        }
        System.out.println("FLOOD_CLIENT_MIXINS_PASS targets="+names.length);
    }
}
