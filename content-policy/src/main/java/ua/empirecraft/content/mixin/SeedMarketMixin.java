package ua.empirecraft.content.mixin;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.content.Policy;

@Pseudo
@Mixin(targets="ua.empirecraft.worldrules.SeedMarket",remap=false)
public abstract class SeedMarketMixin {
    @Inject(method="stock",at=@At("RETURN"),cancellable=true)
    private static void empirecraft$stock(CallbackInfoReturnable<List<String>> ci){ci.setReturnValue(ci.getReturnValue().stream().filter(id->!Policy.blocked(id)).toList());}
}
