package ua.townyrecruits.mixin;

import java.util.UUID;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraftforge.network.NetworkEvent;
import ua.townyrecruits.MilitarySystem;

@Mixin(targets = "com.talhanation.recruits.network.MessageDisbandGroup", remap = false)
public abstract class RegimentalDismissMixin {
    @Shadow private UUID groupUUID;
    @Inject(method = "executeServerSide", at = @At("HEAD"), cancellable = true)
    private void keepNationalCompany(NetworkEvent.Context context, CallbackInfo ci) {
        if (MilitarySystem.protectedGroup(groupUUID)) ci.cancel();
    }
}
