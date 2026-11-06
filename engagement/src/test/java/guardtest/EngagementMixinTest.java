package guardtest;

import java.util.List;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

public final class EngagementMixinTest {
    public static void main(String[] args)throws Exception {
        MixinBootstrap.init();var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.SERVER);Mixins.addConfiguration("engagement.mixins.json");
        var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();com.llamalad7.mixinextras.MixinExtrasBootstrap.init();int count=0;
        for(String name:List.of("com.quintonc.vs_sails.protection.ProtectionEvents","com.quintonc.vs_sails.protection.ShipProtection","ua.townyrecruits.Bridge",
            "net.minecraft.world.level.Level","net.minecraft.world.entity.LivingEntity","net.minecraft.world.entity.Entity","net.minecraft.world.level.Explosion","net.minecraft.world.level.block.DispenserBlock",
            "net.minecraft.world.entity.projectile.AbstractArrow","net.minecraft.world.entity.projectile.ThrowableProjectile","net.minecraft.world.entity.projectile.AbstractHurtingProjectile","net.minecraft.world.entity.projectile.FireworkRocketEntity","net.minecraft.world.entity.item.PrimedTnt","net.minecraft.world.entity.AreaEffectCloud",
            "net.mehvahdjukaar.moonlight.api.entity.ImprovedProjectileEntity","net.mehvahdjukaar.supplementaries.common.entities.BombEntity","net.mehvahdjukaar.supplementaries.common.block.tiles.CannonBlockTile",
            "rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile","rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile","rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst",
            "ua.empirecraft.shipphysics.CollisionDamage","ua.empirecraft.shipphysics.CollisionDamage$Frame")){
            var node=service.getClassNode(name);if(!transformer.transformClass(env,name,node))throw new AssertionError("No engagement injection: "+name);
            // MixinExtras generates wrapper methods before the launcher's final
            // class-writer pass. Verify their materialized maxs and empty handlers.
            var writer=new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);node.accept(writer);node=new ClassNode();new org.objectweb.asm.ClassReader(writer.toByteArray()).accept(node,org.objectweb.asm.ClassReader.EXPAND_FRAMES);
            for(MethodNode method:node.methods)if((method.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
            count++;System.out.println("PASS exact-JAR engagement Mixin: "+name);
        }
        System.out.println("ENGAGEMENT_MIXINS_PASS targets="+count);
    }
}
