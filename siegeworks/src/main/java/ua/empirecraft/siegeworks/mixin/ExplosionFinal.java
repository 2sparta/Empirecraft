package ua.empirecraft.siegeworks.mixin;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.siegeworks.Explosions;
@Mixin(value=Explosion.class,remap=false)
public abstract class ExplosionFinal {
 @Shadow @Final private Level f_46012_;
 @Inject(method="m_46075_(Z)V",at=@At("HEAD"),require=1)
 private void filter(boolean particles,CallbackInfo ci){Explosions.filter((Explosion)(Object)this,f_46012_);}
}
