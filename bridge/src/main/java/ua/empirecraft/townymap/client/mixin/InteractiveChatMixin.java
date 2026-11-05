package ua.empirecraft.townymap.client.mixin;

import com.ebicep.chatplus.hud.ChatManager;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dispatch the original RUN_COMMAND/SUGGEST_COMMAND style before ChatPlus selection handlers consume it. */
@Mixin(ChatScreen.class)
public abstract class InteractiveChatMixin {
    @Inject(method="m_6375_",at=@At("HEAD"),cancellable=true,remap=false)
    private void empirecraft$click(double x,double y,int button,CallbackInfoReturnable<Boolean> callback){
        if(button!=0||!ModList.get().isLoaded("chatplus"))return;
        var tab=ChatManager.INSTANCE.getGlobalSelectedTab();if(tab==null)return;
        var style=tab.getComponentStyleAt(x,y);
        if(style!=null&&style.getClickEvent()!=null&&((Screen)(Object)this).handleComponentClicked(style))callback.setReturnValue(true);
    }
}
