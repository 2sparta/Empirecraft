package ua.empirecraft.content.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.content.Stacks;

@Mixin(value=ItemStack.class,remap=false)
public abstract class StackMixin {
    @Shadow private Item f_41589_;
    @Shadow private CompoundTag f_41590_;
    @Unique private void empirecraft$content() {
        if(!Stacks.active || f_41589_==null)return;
        Item original=f_41589_,next=Stacks.replacement(original);
        if(next!=original) {
            Stacks.clean(f_41590_,original.m_41462_(),next.m_41462_());
            ((ItemStack)(Object)this).setItem(next);
        }
    }
    @Inject(method="m_41720_()Lnet/minecraft/world/item/Item;",at=@At("HEAD"))
    private void empirecraft$item(CallbackInfoReturnable<Item> ci){empirecraft$content();}
    @Inject(method={"m_41783_()Lnet/minecraft/nbt/CompoundTag;","m_41784_()Lnet/minecraft/nbt/CompoundTag;"},at=@At("HEAD"))
    private void empirecraft$tag(CallbackInfoReturnable<CompoundTag> ci){empirecraft$content();}
    @Inject(method="m_41739_(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",at=@At("HEAD"))
    private void empirecraft$save(CompoundTag tag,CallbackInfoReturnable<CompoundTag> ci){empirecraft$content();}
    @Inject(method="m_41751_(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("TAIL"))
    private void empirecraft$setTag(CompoundTag tag,CallbackInfo ci){empirecraft$content();}
}
