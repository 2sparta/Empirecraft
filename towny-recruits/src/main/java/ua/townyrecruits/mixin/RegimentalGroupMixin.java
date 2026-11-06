package ua.townyrecruits.mixin;

import com.talhanation.recruits.world.*;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.townyrecruits.*;

/** Regimental ownership and membership are edited through Towny; ordinary groups retain native editing. */
@Mixin(value = RecruitsGroupsManager.class, remap = false)
public abstract class RegimentalGroupMixin {
    @Inject(method = "addOrUpdateGroup", at = @At("HEAD"), cancellable = true)
    private void protect(ServerLevel level, ServerPlayer player, RecruitsGroup incoming, CallbackInfo ci) {
        if (incoming == null || !MilitarySystem.protectedGroup(incoming.getUUID())) return;
        if(RegimentExchange.locked(incoming.getUUID())){ci.cancel();return;}
        var company = MilitarySystem.current().data.companies.get(incoming.getUUID());
        if (player == null || !company.commander().equals(player.m_20148_()) || !company.commander().equals(incoming.getPlayerUUID())
            || incoming.removed || incoming.disbandContext != null && incoming.disbandContext.disband) ci.cancel();
    }
    @Inject(method = "mergeGroups", at = @At("HEAD"), cancellable = true)
    private void merge(RecruitsGroup source, RecruitsGroup destination, ServerLevel level, CallbackInfo ci) {
        if (MilitarySystem.protectedGroup(source.getUUID()) || MilitarySystem.protectedGroup(destination.getUUID())) ci.cancel();
    }
    @Inject(method = "splitGroup", at = @At("HEAD"), cancellable = true)
    private void split(RecruitsGroup group, ServerLevel level, CallbackInfo ci) { if (MilitarySystem.protectedGroup(group.getUUID())) ci.cancel(); }
}
