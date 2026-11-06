package ua.empirecraft.engagement.mixin;

import net.minecraft.world.entity.*;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.engagement.*;

@Mixin(value=LivingEntity.class,remap=false)
public abstract class EffectGuardMixin {
    @Inject(method="m_147207_",at=@At("HEAD"),cancellable=true)
    private void effect(MobEffectInstance effect,Entity source,CallbackInfoReturnable<Boolean> ci){
        var self=(LivingEntity)(Object)this;if(self.level().isClientSide||effect.getEffect().isBeneficial()||source==null&&ShotOrigins.current()==null)return;
        var origin=ShotOrigins.find(source);if(origin==null)origin=ShotOrigins.current();
        boolean allow=origin==null?EngagementRuntime.attack(source,self,true):EngagementRuntime.decide(origin.party(),Combatants.target(self),(net.minecraft.server.level.ServerLevel)self.level(),true).allow();
        if(!allow)ci.setReturnValue(false);
    }
}
