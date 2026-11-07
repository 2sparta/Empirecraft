package ua.empirecraft.civic.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.civic.CivicEvents;

@Mixin(targets="com.wdiscute.starcatcher.tournament.TournamentHandler",remap=false)
public abstract class TournamentCatchMixin {
    @Inject(method="addScore",at=@At("RETURN"),remap=false,require=1)
    private static void empirecraft$completed(Player player,@Coerce Object fish,boolean perfect,float percentile,CallbackInfo ci){CivicEvents.caught(player,fish,percentile);}
}
