package ua.townyrecruits.mixin;
import ua.townyrecruits.Bridge;
import ewewukek.musketmod.BulletEntity;
import com.TBK.medieval_boomsticks.server.entity.RoundBallProjectile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Block friendly hit side effects (including Boomsticks shield disabling) before native hit code. */
@Mixin(value={BulletEntity.class,RoundBallProjectile.class},remap=false)
public abstract class ProjectileMixin {
 @Inject(method="m_5790_",at=@At("HEAD"),cancellable=true)
 private void friendlyHit(EntityHitResult hit,CallbackInfo ci){
  Projectile p=(Projectile)(Object)this;
  if(p.m_19749_() instanceof LivingEntity owner && hit.m_82443_() instanceof LivingEntity target && Boolean.FALSE.equals(Bridge.combat(owner,target))){p.m_146870_();ci.cancel();}
 }
}
