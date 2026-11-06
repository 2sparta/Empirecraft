package ua.empirecraft.engagement.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import ua.empirecraft.engagement.*;
import java.util.UUID;

@Pseudo @Mixin(targets="net.mehvahdjukaar.supplementaries.common.block.tiles.CannonBlockTile",remap=false,priority=1100)
public abstract class CannonLaunchMixin {
    @Shadow private UUID controllingPlayer;
    @Shadow private UUID playerWhoIgnitedUUID;
    @WrapMethod(method="shootProjectile") private boolean launch(ServerLevel level,Operation<Boolean> original){
        var tile=(BlockEntity)(Object)this;UUID actor=controllingPlayer==null?playerWhoIgnitedUUID:controllingPlayer;
        try(var ignored=LauncherScopes.block(level,tile.getBlockPos(),actor)){return original.call(level);}
        catch(IllegalArgumentException denied){return false;}
    }
}
