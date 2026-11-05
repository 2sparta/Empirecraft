package ua.townyrecruits.mixin;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.Objects;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Friendly ranks can cross during a formation turn without pushing each other out of their slots. */
@Mixin(value = Entity.class, remap = false)
public abstract class FormationCollisionMixin {
    @Inject(method = "m_7334_", at = @At("HEAD"), cancellable = true)
    private void keepFriendlyRanks(Entity other, CallbackInfo ci) {
        if ((Object)this instanceof AbstractRecruitEntity a && other instanceof AbstractRecruitEntity b
            && a.isInFormation && b.isInFormation && a.holdFormation && b.holdFormation
            && a.getIsOwned() && b.getIsOwned() && Objects.equals(a.getOwnerUUID(), b.getOwnerUUID())) ci.cancel();
    }
}
