package ua.empirecraft.casting.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;import net.minecraft.core.*;import net.minecraft.world.level.block.state.BlockState;
import ua.empirecraft.casting.Metallurgy;
@Mixin(value=CrucibleBlockEntity.class,remap=false)
public abstract class CrucibleMixin {
 @Inject(method="<init>",at=@At("RETURN"))private void ports(BlockPos pos,BlockState state,CallbackInfo ci){var access=(InventoryAccess)(Object)this;access.casting$sides().on(new Metallurgy.Port(access.casting$inventory(),false),Direction.UP).on(new Metallurgy.Port(access.casting$inventory(),true),Direction.Plane.HORIZONTAL);}
}
