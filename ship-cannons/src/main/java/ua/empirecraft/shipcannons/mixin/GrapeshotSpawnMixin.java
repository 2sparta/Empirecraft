package ua.empirecraft.shipcannons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst;
import ua.empirecraft.shipcannons.CannonRecoil;
import ua.empirecraft.shipcannons.GrapeshotDamage;

@Pseudo
@Mixin(targets="rbasamoyai.createbigcannons.munitions.big_cannon.grapeshot.GrapeshotBagProjectile",remap=false)
public abstract class GrapeshotSpawnMixin {
    @WrapOperation(method="disintegrate",at=@At(value="INVOKE",target="Lrbasamoyai/createbigcannons/munitions/fragment_burst/CBCProjectileBurst;spawnConeBurst(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;ID)Lrbasamoyai/ritchiesprojectilelib/projectile_burst/ProjectileBurst;"))
    private ProjectileBurst empirecraft$spread(Level level,EntityType<ProjectileBurst> type,Vec3 pos,Vec3 velocity,int count,double spread,Operation<ProjectileBurst> original) {
        return CannonRecoil.grapeshotEnabled()?GrapeshotDamage.spawn(level,type,pos,velocity,count,spread,(Projectile)(Object)this):original.call(level,type,pos,velocity,count,spread);
    }
}
