package ua.empirecraft.siegeworks.vs.mixin;
import me.mss1r.siegeworks.entity.base.AbstractSiegeEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.siegeworks.vs.Decks;
/** Native Entity.move still performs VS collisions; avoid the duplicate terrain OBB scan on deck. */
@Mixin(targets="me.mss1r.siegeworks.gameplay.collision.SiegeTerrainCollision",remap=false)public abstract class DeckMovement {
 @Inject(method="clampMovement",at=@At("HEAD"),cancellable=true)private static void clamp(AbstractSiegeEntity e,Vec3 wanted,CallbackInfoReturnable<Vec3> result){if(Decks.contact(e)!=null)result.setReturnValue(wanted);}
 @Inject(method="unstick",at=@At("HEAD"),cancellable=true)private static void unstick(AbstractSiegeEntity e,CallbackInfo result){if(Decks.contact(e)!=null)result.cancel();}
}
