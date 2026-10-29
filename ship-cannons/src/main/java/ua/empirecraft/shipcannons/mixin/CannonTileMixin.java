package ua.empirecraft.shipcannons.mixin;
import net.mehvahdjukaar.supplementaries.common.block.tiles.CannonBlockTile;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import ua.empirecraft.shipcannons.CannonAccess;
import ua.empirecraft.shipcannons.CannonRecoil;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Override the inherited interface checks only for cannons; keep the original edit owner and range. */
@Mixin(value=CannonBlockTile.class,remap=false)
public abstract class CannonTileMixin implements Container {
    @Shadow private float pitch;
    @Shadow private float yaw;
    @Inject(method="shootProjectile",at=@At("RETURN"))
    private void empirecraft$recoil(ServerLevel level,CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValue())CannonRecoil.fired((CannonBlockTile)(Object)this,level,pitch,yaw);
    }
    public boolean isEditingPlayer(Player player) {
        var tile=(CannonBlockTile)(Object)this;
        if(tile.getLevel()!=null && tile.getLevel().isClientSide)return CannonAccess.nearby(tile,player);
        return player.getUUID().equals(CannonAccess.validate(tile));
    }
    public boolean isOtherPlayerEditing(Player player) {
        var owner=CannonAccess.validate((CannonBlockTile)(Object)this);
        return owner!=null && !owner.equals(player.getUUID());
    }
    @Override public boolean stillValid(Player player){return CannonAccess.nearby((CannonBlockTile)(Object)this,player);}
}

