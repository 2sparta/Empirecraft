package ua.empirecraft.chinese.mixin;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.mirrorloong.chineseweapons.entity.SongJavelinProjectile",remap=false)
public abstract class JavelinQuality {
 @Inject(method="<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",at=@At("RETURN"),require=1)
 private void chinese$snapshot(Level level,LivingEntity owner,ItemStack stack,CallbackInfo ci){ua.empirecraft.forging.RangedQuality.mark((Entity)(Object)this,stack);}
}
