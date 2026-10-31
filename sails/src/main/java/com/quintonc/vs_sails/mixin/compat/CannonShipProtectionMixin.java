package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.protection.ShipProtection;
import com.quintonc.vs_sails.protection.CannonShipGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.UUID;

@Pseudo
@Mixin(targets="net.mehvahdjukaar.supplementaries.common.block.tiles.CannonBlockTile",remap=false)
public abstract class CannonShipProtectionMixin implements CannonShipGuard {
    @Shadow(remap=false) private UUID playerWhoIgnitedUUID;
    @Shadow(remap=false) private UUID controllingPlayer;
    @Override public void vs_sails$validateEditor() {
        if(controllingPlayer==null)return;
        var tile=(BlockEntity)(Object)this;var level=tile.getLevel();
        if(level!=null && !level.isClientSide && !ShipProtection.allows(level.getPlayerByUUID(controllingPlayer),level,tile.getBlockPos()))controllingPlayer=null;
    }
    @Inject(method="tick",at=@At("HEAD"))
    private static void empirecraft$editor(Level level,BlockPos pos,BlockState state,@Coerce Object tile,CallbackInfo ci) {
        if(!level.isClientSide)((CannonShipGuard)tile).vs_sails$validateEditor();
    }
    @Inject(method="setAttributes",at=@At("HEAD"),cancellable=true)
    private void empirecraft$aim(float yaw,float pitch,byte power,boolean fire,Player player,CallbackInfo ci) {
        var tile=(BlockEntity)(Object)this;
        if(!ShipProtection.allows(player,tile.getLevel(),tile.getBlockPos()))ci.cancel();
    }
    @Inject(method="tryOpeningEditGui",at=@At("HEAD"),cancellable=true)
    private void empirecraft$open(ServerPlayer player,BlockPos pos,ItemStack stack,CallbackInfoReturnable<Boolean> cir) {
        var tile=(BlockEntity)(Object)this;
        if(!ShipProtection.allows(player,tile.getLevel(),tile.getBlockPos()))cir.setReturnValue(false);
    }
    @Inject(method="ignite",at=@At("HEAD"),cancellable=true)
    private void empirecraft$ignite(Entity source,CallbackInfo ci) {
        var tile=(BlockEntity)(Object)this;
        if(!ShipProtection.allows(source instanceof Player p?p:null,tile.getLevel(),tile.getBlockPos()))ci.cancel();
    }
    @Inject(method="shootProjectile",at=@At("HEAD"),cancellable=true)
    private void empirecraft$fire(ServerLevel level,CallbackInfoReturnable<Boolean> cir) {
        var tile=(BlockEntity)(Object)this;Player actor=playerWhoIgnitedUUID==null?null:level.getPlayerByUUID(playerWhoIgnitedUUID);
        if(!ShipProtection.allows(actor,level,tile.getBlockPos()))cir.setReturnValue(false);
    }
}
