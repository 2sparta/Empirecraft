package ua.empirecraft.kitchenclienttest.mixin;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Fixture only: an offline Forge world has no Bukkit permission service; never grants extra permissions. */
@Mixin(targets="ua.townyrecruits.RecruitmentCommands",remap=false)
public final class OfflineTownyCommands {
    @Inject(method="admin",at=@At("HEAD"),cancellable=true)
    private static void offlineOnly(CommandSourceStack source,CallbackInfoReturnable<Boolean> result) {
        if (!source.getServer().isDedicatedServer()) result.setReturnValue(false);
    }
}
