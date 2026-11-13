package ua.empirecraft.packfixes.mixin;

import java.util.List;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.chunk.HashMapPalette;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.packfixes.PaletteEntries;

@Mixin(value=HashMapPalette.class,remap=false)
public abstract class PaletteEntriesMixin<T> {
    @Shadow @Final private CrudeIncrementalIntIdentityHashBiMap<T> f_62658_;
    @Inject(method="m_187917_",at=@At("HEAD"),cancellable=true)
    private void empirecraft$denseEntries(CallbackInfoReturnable<List<T>> callback) {
        var result=PaletteEntries.dense(f_62658_);
        if(result!=null)callback.setReturnValue(result);
    }
}
