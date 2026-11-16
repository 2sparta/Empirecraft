package ua.empirecraft.armory.mixin;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=LivingEntity.class,remap=false)
public abstract class OttomanShieldMixin {
    @Inject(method="m_6728_",at=@At("HEAD"),cancellable=true,require=1)
    private void armory$noShieldKnockback(LivingEntity attacker,CallbackInfo ci){var entity=(LivingEntity)(Object)this;var key=ForgeRegistries.ITEMS.getKey(entity.getUseItem().getItem());if(key!=null&&key.toString().equals("hamzabus_ottoman_mod:ottoman_shield"))ci.cancel();}
}
