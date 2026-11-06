package ua.empirecraft.engagement.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.engagement.*;

@Mixin(value=Entity.class,remap=false)
public abstract class FireGuardMixin {
    @Inject(method="m_20254_",at=@At("HEAD"),cancellable=true) private void ignite(int seconds,CallbackInfo ci){
        var self=(Entity)(Object)this;var source=ShotOrigins.current();if(source!=null&&!self.level().isClientSide&&!EngagementRuntime.decide(source.party(),Combatants.target(self),(net.minecraft.server.level.ServerLevel)self.level(),true).allow())ci.cancel();
    }
}
