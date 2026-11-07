package ua.empirecraft.leaves.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.leaves.client.ClientContext;
import ua.empirecraft.leaves.client.ClientSeasons;

@Mixin(targets="net.minecraft.client.Minecraft",remap=false)
public abstract class ClientContextMixin implements ClientContext {
    @Accessor("f_91073_") public abstract ClientLevel empirecraft$level();
    @Accessor("f_91060_") public abstract LevelRenderer empirecraft$renderer();
    @Inject(method="<init>",at=@At("RETURN"))
    private void empirecraft$attach(CallbackInfo ci) {ClientSeasons.attach(this);}
}
