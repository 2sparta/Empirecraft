package ua.empirecraft.noend.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Holder;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.noend.*;

@Mixin(value=ItemStack.class,remap=false)
public abstract class StackMixin implements RawStack {
    @Shadow @Final @Mutable private Item f_41589_;
    @Shadow @Final @Mutable private Holder.Reference<Item> delegate;
    @Shadow private CompoundTag f_41590_;
    @Unique public Item empirecraft$rawItem(){return f_41589_;}
    @Unique private void empirecraft$convert(){
        if(f_41589_==null)return;
        Item original=f_41589_,next=Stacks.replacement(original);
        if(next!=original){Stacks.clean(f_41590_,original.m_41462_(),next.m_41462_());f_41589_=next;delegate=ForgeRegistries.ITEMS.getDelegateOrThrow(next);}
    }
    @Inject(method="m_41720_()Lnet/minecraft/world/item/Item;",at=@At("HEAD"))
    private void empirecraft$item(CallbackInfoReturnable<Item> callback){empirecraft$convert();}
    @Inject(method={"m_41783_()Lnet/minecraft/nbt/CompoundTag;","m_41784_()Lnet/minecraft/nbt/CompoundTag;"},at=@At("HEAD"))
    private void empirecraft$tag(CallbackInfoReturnable<CompoundTag> callback){empirecraft$convert();}
    @Inject(method="m_41739_(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",at=@At("HEAD"))
    private void empirecraft$save(CompoundTag tag,CallbackInfoReturnable<CompoundTag> callback){empirecraft$convert();}
    @Inject(method="m_41751_(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("TAIL"))
    private void empirecraft$setTag(CompoundTag tag,CallbackInfo callback){empirecraft$convert();}
}
