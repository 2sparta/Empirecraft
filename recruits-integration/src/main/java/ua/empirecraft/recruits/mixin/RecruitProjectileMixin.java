package ua.empirecraft.recruits.mixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.recruits.ArmyHooks;
@Mixin(value=Projectile.class,remap=false)
public abstract class RecruitProjectileMixin {
 @Inject(method="m_5602_(Lnet/minecraft/world/entity/Entity;)V",at=@At("TAIL"))
 private void army$provenance(Entity owner,CallbackInfo ci){ArmyHooks.remember((Entity)(Object)this,owner);}
}
