package ua.empirecraft.siegeworks.vs.mixin;
import me.mss1r.siegeworks.entity.base.AbstractSiegeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.siegeworks.vs.Decks;
@Mixin(value=AbstractSiegeEntity.class,remap=false)public abstract class DeckTick {
 @Inject(method="m_8119_()V",at=@At("HEAD"))private void tick(CallbackInfo ci){Decks.tick((AbstractSiegeEntity)(Object)this);}
}
