package ua.empirecraft.casting.mixin;
import net.minecraft.world.entity.item.ItemEntity;import net.minecraft.core.particles.ParticleTypes;import net.minecraft.server.level.ServerLevel;import net.minecraft.sounds.*;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;import ua.empirecraft.casting.Thermal;
@Mixin(value=ItemEntity.class,remap=false)
public abstract class ItemWaterMixin {
 @Inject(method="m_8119_",at=@At("TAIL"))private void cool(CallbackInfo ci){var e=(ItemEntity)(Object)this;if(!(e.level() instanceof ServerLevel level))return;var previous=e.getItem();var stack=Thermal.normalize(previous);boolean cooled=e.isInWater()&&Thermal.quench(stack);if(cooled){level.sendParticles(ParticleTypes.CLOUD,e.getX(),e.getY()+.2,e.getZ(),6,.1,.1,.1,.01);level.playSound(null,e.blockPosition(),SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,.5f,1.5f);}if(cooled||stack!=previous)e.setItem(stack.copy());}
}
