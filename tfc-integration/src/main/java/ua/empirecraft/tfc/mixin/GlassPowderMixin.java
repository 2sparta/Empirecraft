package ua.empirecraft.tfc.mixin;

import net.dries007.tfc.common.capabilities.glass.GlassOperation;
import java.util.*;
import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.tfc.Integration;

/** Carbon colouring uses the pack's charcoal; retired graphite stays unobtainable. */
@Mixin(value=GlassOperation.class,remap=false)
public abstract class GlassPowderMixin {
    @Shadow @Final @Mutable public static Supplier<Map<Item,GlassOperation>> POWDERS;
    @Inject(method="<clinit>",at=@At("TAIL"))
    private static void empirecraft$charcoal(CallbackInfo ci){
        Supplier<Map<Item,GlassOperation>> original=POWDERS;
        // Keep deferred registry lookup deferred, and update the same map used by JEI.
        POWDERS=Suppliers.memoize(()->{
            Map<Item,GlassOperation> powders=new HashMap<>(original.get());
            powders.keySet().removeIf(item->Integration.id(item).equals("tfc:powder/graphite"));
            powders.put(Items.CHARCOAL,GlassOperation.GRAPHITE);
            return Map.copyOf(powders);
        });
    }
}
