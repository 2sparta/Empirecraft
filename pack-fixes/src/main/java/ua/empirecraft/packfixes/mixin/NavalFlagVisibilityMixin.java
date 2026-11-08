package ua.empirecraft.packfixes.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import ua.empirecraft.engagement.NavalFlags;
import ua.empirecraft.packfixes.FlagClearance;

@Mixin(value=NavalFlags.class,remap=false)
public abstract class NavalFlagVisibilityMixin {
    @Inject(method="visible",at=@At("RETURN"),cancellable=true)
    private static void empirecraft$windCloth(ServerLevel level,LoadedServerShip ship,NavalFlags.FlagEntity flag,CallbackInfoReturnable<Boolean> result) {
        if(result.getReturnValue()&&!FlagClearance.visible(level,ship,flag))result.setReturnValue(false);
    }
}
