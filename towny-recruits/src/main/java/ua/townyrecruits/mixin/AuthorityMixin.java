package ua.townyrecruits.mixin;
import com.talhanation.recruits.FactionEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Faction writes use the manager directly from the Towny snapshot. Native faction controls are disabled. */
@Mixin(value=FactionEvents.class,remap=false)
public abstract class AuthorityMixin {
    @Inject(method="onTypeCommandEvent",at=@At("HEAD"),cancellable=true)
    private void nativeCommands(CallbackInfo ci){ci.cancel();}

    @Inject(method={"createTeam","modifyTeam","leaveTeam","addPlayerToTeam","sendJoinRequest","removeOfflinePlayerFromTeam","tryToRemoveFromTeam","removeTeam","assignToTeamMate"},at=@At("HEAD"),cancellable=true)
    private static void townyOnly(CallbackInfo ci){ci.cancel();}
}
