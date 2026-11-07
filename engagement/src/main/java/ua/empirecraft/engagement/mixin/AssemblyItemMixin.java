package ua.empirecraft.engagement.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import ua.empirecraft.engagement.ShipAssemblyGate;

@Mixin(targets={"org.valkyrienskies.mod.common.item.ShipAssemblerItem","org.valkyrienskies.mod.common.item.ShipCreatorItem","com.quintonc.vs_sails.items.DedicationBottle"},remap=false)
public abstract class AssemblyItemMixin {
    @WrapMethod(method="m_6225_")
    private InteractionResult approvedUse(UseOnContext context,Operation<InteractionResult> original){return ShipAssemblyGate.use(context,original);}
}
