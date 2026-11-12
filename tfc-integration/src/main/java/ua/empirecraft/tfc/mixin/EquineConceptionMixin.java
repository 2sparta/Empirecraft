package ua.empirecraft.tfc.mixin;

import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.equine.EquineGenetics;

/** A completed TFC pregnancy rechecks fauna admission, without conceiving again. */
@Mixin(value=EquineGenetics.class,remap=false)
public abstract class EquineConceptionMixin {
    @Inject(method="conceive",at=@At("HEAD"),cancellable=true)
    private void empirecraft$completedPregnancy(BabyEntitySpawnEvent event,CallbackInfo ci){
        if(event.getChild()!=null&&event.getChild().getPersistentData().getBoolean("EmpirecraftTfcBirthInProgress"))ci.cancel();
    }
}
