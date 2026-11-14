package ua.empirecraft.casting.mixin;

import net.stirdrem.overgeared.item.ToolType;
import net.stirdrem.overgeared.item.ToolTypeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drafting clients do not load server datapacks, so register their choices here too. */
@Mixin(value=ToolTypeRegistry.class,remap=false)
public abstract class ToolTypesMixin {
 @Inject(method="init",at=@At("TAIL"))
 private static void parts(CallbackInfo ci){
  for(String type:new String[]{"javelin_head","scythe_blade","pickaxe_head","axe_head","shovel_head","hoe_head","hammer_head","knife_blade","saw_blade","chisel_head"})ToolType.of(type);
 }
}
