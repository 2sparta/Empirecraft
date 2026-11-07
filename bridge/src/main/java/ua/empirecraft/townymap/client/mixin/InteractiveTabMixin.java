package ua.empirecraft.townymap.client.mixin;

import com.ebicep.chatplus.features.chattabs.ChatTab;
import com.ebicep.chatplus.hud.ChatManager;
import ua.empirecraft.townymap.client.InteractiveComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also show directly addressed interactive system messages in the recipient's selected channel. */
@Pseudo
@Mixin(targets="com.ebicep.chatplus.features.chattabs.ChatTab",remap=false)
public abstract class InteractiveTabMixin {
    @Inject(method="matches(Lnet/minecraft/network/chat/Component;)Z",at=@At("HEAD"),cancellable=true,remap=false)
    private void empirecraft$addressed(Component message,CallbackInfoReturnable<Boolean> callback){
        if((Object)this==ChatManager.INSTANCE.getGlobalSelectedTab()&&InteractiveComponents.actionable(message))callback.setReturnValue(true);
    }
}
