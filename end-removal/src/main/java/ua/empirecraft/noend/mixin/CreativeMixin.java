package ua.empirecraft.noend.mixin;

import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.noend.Stacks;

/** Preserve raw identities while native creative builders run; the final event removes them. */
@Mixin(value=CreativeModeTabs.class,remap=false)
public abstract class CreativeMixin {
    @Inject(method="m_269226_(Lnet/minecraft/world/flag/FeatureFlagSet;ZLnet/minecraft/core/HolderLookup$Provider;)Z",at=@At("HEAD"))
    private static void empirecraft$begin(CallbackInfoReturnable<Boolean> callback){Stacks.beginCreative();}
    @Inject(method="m_269226_(Lnet/minecraft/world/flag/FeatureFlagSet;ZLnet/minecraft/core/HolderLookup$Provider;)Z",at=@At("RETURN"))
    private static void empirecraft$end(CallbackInfoReturnable<Boolean> callback){Stacks.endCreative();}
}
