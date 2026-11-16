package ua.empirecraft.casting.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;import net.minecraft.core.*;import net.minecraft.world.level.block.state.BlockState;
import ua.empirecraft.casting.Metallurgy;
@Mixin(value=CrucibleBlockEntity.class,remap=false)
public abstract class CrucibleMixin {
 @Inject(method="<init>",at=@At("RETURN"))private void ports(BlockPos pos,BlockState state,CallbackInfo ci){var access=(InventoryAccess)(Object)this;access.casting$sides().on(new Metallurgy.Port(access.casting$inventory(),false),Direction.UP).on(new Metallurgy.Port(access.casting$inventory(),true),Direction.Plane.HORIZONTAL);}
 @Inject(method="isItemValid",at=@At("HEAD"),cancellable=true)
 private void flux(int slot,net.minecraft.world.item.ItemStack stack,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci){if(slot>=0&&slot<9&&ua.empirecraft.casting.Thermal.id(stack).equals("tfc:powder/flux"))ci.setReturnValue(true);}
 @Inject(method="serverTick",at={@At("HEAD"),@At("RETURN")} )private static void refining(net.minecraft.world.level.Level level,BlockPos pos,BlockState state,CrucibleBlockEntity crucible,CallbackInfo ci){ua.empirecraft.casting.CrucibleRefining.step(crucible);}
 // Reserve flux for crucible refining, including while cold or waiting for incoming metal.
 // Its ordinary lime-heating recipe remains available to all other TFC heat sources.
 @Redirect(method={"updateCaches","setAndUpdateSlots"},at=@At(value="INVOKE",target="Lnet/dries007/tfc/common/recipes/HeatingRecipe;getRecipe(Lnet/minecraft/world/item/ItemStack;)Lnet/dries007/tfc/common/recipes/HeatingRecipe;"))
 private net.dries007.tfc.common.recipes.HeatingRecipe fluxReagent(net.minecraft.world.item.ItemStack stack){return ua.empirecraft.casting.CrucibleRefining.isFlux(stack)?null:net.dries007.tfc.common.recipes.HeatingRecipe.getRecipe(stack);}
}
