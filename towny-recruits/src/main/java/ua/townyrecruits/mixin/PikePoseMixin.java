package ua.townyrecruits.mixin;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.townyrecruits.tactics.TacticalEquipment;

@Mixin(value = HumanoidModel.class, remap = false)
public abstract class PikePoseMixin {
    @Inject(method = "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void brace(LivingEntity entity, float a, float b, float c, float d, float e, CallbackInfo ci) {
        if (!(entity instanceof AbstractRecruitEntity r) || !r.getShouldBlock() || !TacticalEquipment.pike(r)) return;
        HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
        model.f_102811_.f_104203_ = -1.35f; model.f_102812_.f_104203_ = -1.25f;
        model.f_102811_.f_104204_ = -0.18f; model.f_102812_.f_104204_ = 0.25f;
        model.f_102811_.f_104205_ = 0; model.f_102812_.f_104205_ = 0;
    }
}
