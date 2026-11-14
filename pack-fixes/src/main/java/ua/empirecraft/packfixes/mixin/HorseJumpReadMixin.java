package ua.empirecraft.packfixes.mixin;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bukkit/CoreProtect reads vanilla jump strength even on modded mount classes without it. */
@Mixin(value=AbstractHorse.class,remap=false)
public abstract class HorseJumpReadMixin {
    @Inject(method="m_30626_",at=@At("HEAD"),cancellable=true)
    private void empirecraft$missingJumpAttribute(CallbackInfoReturnable<Double> callback) {
        var horse=(AbstractHorse)(Object)this;
        if(!horse.getAttributes().hasAttribute(Attributes.JUMP_STRENGTH))
            callback.setReturnValue(Attributes.JUMP_STRENGTH.getDefaultValue());
    }
}
