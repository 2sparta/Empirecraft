package ua.empirecraft.engagement.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import ua.empirecraft.engagement.ShotOrigins;

@Pseudo @Mixin(targets={
 "net.minecraft.world.entity.projectile.AbstractArrow","net.minecraft.world.entity.projectile.ThrowableProjectile",
 "net.minecraft.world.entity.projectile.AbstractHurtingProjectile","net.minecraft.world.entity.projectile.FireworkRocketEntity",
 "net.minecraft.world.entity.item.PrimedTnt","net.minecraft.world.entity.AreaEffectCloud",
 "net.mehvahdjukaar.moonlight.api.entity.ImprovedProjectileEntity",
 "net.mehvahdjukaar.supplementaries.common.entities.BombEntity",
 "rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile",
 "rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile",
 "rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst"
},remap=false)
public abstract class ProjectileScopeMixin {
    @WrapMethod(method="m_8119_") private void projectile(Operation<Void> original){try(var ignored=ShotOrigins.enter((Entity)(Object)this)){original.call();}}
}
